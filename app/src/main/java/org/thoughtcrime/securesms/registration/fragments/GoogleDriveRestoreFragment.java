package org.thoughtcrime.securesms.registration.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import net.sqlcipher.database.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.AppInitialization;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupBase;
import org.thoughtcrime.securesms.backup.FullBackupImporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DriveBackupUtil;
import org.thoughtcrime.securesms.util.GoogleDriveServiceHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class GoogleDriveRestoreFragment extends BaseRegistrationFragment {

    private static final String         TAG                                     = Log.tag(GoogleDriveRestoreFragment.class);
    private static final short          OPEN_DOCUMENT_TREE_RESULT_CODE          = 13782;
    private Pair<String, String>        latestBackup;

    private TextView                    restoreBackupSize;
    private TextView                    restoreBackupTime;
    private TextView                    restoreBackupProgress;
    private TextView                    restoreBackupTitle;
    private CircularProgressButton      restoreButton;
    private GoogleDriveServiceHelper    serviceHelper;
    private View                        skipRestoreButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration_google_drive_restore, container, false);
    }

    @RequiresApi(24)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

        serviceHelper           = GoogleDriveServiceHelper.serviceHelper;
        restoreBackupProgress   = view.findViewById(R.id.drive_backup_progress_text);
        restoreButton           = view.findViewById(R.id.drive_restore_button);
        restoreBackupSize       = view.findViewById(R.id.drive_backup_size_text);
        restoreBackupTime       = view.findViewById(R.id.drive_backup_created_text);
        skipRestoreButton       = view.findViewById(R.id.drive_skip_restore_button);
        restoreBackupTitle      = view.findViewById(R.id.drive_restore_backup_title);

        restoreButton.setOnClickListener(v -> {
//                if (true) return;
                // TODO: Temporary. There's no need for it since we skip if there's no google drive backup.
                if (latestBackup == null) {
                    Log.i(TAG, "No Backup found!");
                    return;
                }
                setLoading();
                DriveBackupUtil.getBackupSync(serviceHelper, latestBackup, backup -> {
                    Log.i(TAG, "Created cached backup file");
                    Log.d(TAG, Objects.requireNonNull(backup).getUri().toString());
                    Log.i(TAG, "Restoring backup...");
                    handleRestore(v.getContext(), backup);
                });
        });
        skipRestoreButton.setOnClickListener(unused -> Navigation.findNavController(requireView()).navigate(GoogleDriveRestoreFragmentDirections.actionSkip()));
        initialiseView();
    }

    // TODO: Create a common base classes for following restoreMethods

    // ******* START OF COMMON METHODS *******

    private enum BackupImportResult {
        SUCCESS,
        FAILURE_VERSION_DOWNGRADE,
        FAILURE_UNKNOWN
    }

    private void handleRestore(@NonNull Context context, @NonNull BackupUtil.BackupInfo backup) {
        View     view   = LayoutInflater.from(context).inflate(R.layout.enter_backup_passphrase_dialog, null);
        EditText prompt = view.findViewById(R.id.restore_passphrase_input);

        prompt.addTextChangedListener(new RestoreBackupFragment.PassphraseAsYouTypeFormatter());

        new AlertDialog.Builder(context)
                .setTitle(R.string.RegistrationActivity_enter_backup_passphrase)
                .setView(view)
                .setPositiveButton(R.string.RegistrationActivity_restore, (dialog, which) -> {
                    InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(prompt.getWindowToken(), 0);

                    String passphrase = prompt.getText().toString();

                    restoreAsynchronously(context, backup, passphrase);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        Log.i(TAG, "Prompt for backup passphrase shown to user.");
    }

    @SuppressLint("StaticFieldLeak")
    private void restoreAsynchronously(@NonNull Context context,
                                       @NonNull BackupUtil.BackupInfo backup,
                                       @NonNull String passphrase)
    {
        new AsyncTask<Void, Void, GoogleDriveRestoreFragment.BackupImportResult>() {
            @Override
            protected GoogleDriveRestoreFragment.BackupImportResult doInBackground(Void... voids) {
                try {
                    Log.i(TAG, "Starting backup restore.");

                    SQLiteDatabase database = DatabaseFactory.getBackupDatabase(context);

                    BackupPassphrase.set(context, passphrase);
                    FullBackupImporter.importFile(context,
                            AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                            database,
                            backup.getUri(),
                            passphrase);

                    DatabaseFactory.upgradeRestored(context, database);
                    NotificationChannels.restoreContactNotificationChannels(context);

                    enableBackups(context);

                    AppInitialization.onPostBackupRestore(context);

                    Log.i(TAG, "Backup restore complete.");
                    return GoogleDriveRestoreFragment.BackupImportResult.SUCCESS;
                } catch (FullBackupImporter.DatabaseDowngradeException e) {
                    Log.w(TAG, "Failed due to the backup being from a newer version of Signal.", e);
                    return GoogleDriveRestoreFragment.BackupImportResult.FAILURE_VERSION_DOWNGRADE;
                } catch (IOException e) {
                    Log.w(TAG, e);
                    return GoogleDriveRestoreFragment.BackupImportResult.FAILURE_UNKNOWN;
                }
            }

            @Override
            protected void onPostExecute(@NonNull GoogleDriveRestoreFragment.BackupImportResult result) {
                cancelLoading();

                restoreBackupProgress.setText("");

                switch (result) {
                    case SUCCESS:
                        Log.i(TAG, "Successful backup restore.");
                        break;
                    case FAILURE_VERSION_DOWNGRADE:
                        Toast.makeText(context, R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show();
                        break;
                    case FAILURE_UNKNOWN:
                        Toast.makeText(context, R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show();
                        break;
                }
            }
        }.execute();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull FullBackupBase.BackupEvent event) {
        int count = event.getCount();

        if (count == 0) {
            restoreBackupProgress.setText(R.string.RegistrationActivity_checking);
        } else {
            restoreBackupProgress.setText(getString(R.string.RegistrationActivity_d_messages_so_far, count));
        }

        setSpinning(restoreButton);
        skipRestoreButton.setVisibility(View.INVISIBLE);

        if (event.getType() == FullBackupBase.BackupEvent.Type.FINISHED) {
            if (BackupUtil.isUserSelectionRequired(requireContext()) && !BackupUtil.canUserAccessBackupDirectory(requireContext())) {
                displayConfirmationDialog(requireContext());
            } else {
                Navigation.findNavController(requireView())
                        .navigate(GoogleDriveRestoreFragmentDirections.actionRestore());
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == OPEN_DOCUMENT_TREE_RESULT_CODE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri backupDirectoryUri = data.getData();
            int takeFlags          = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

            SignalStore.settings().setSignalBackupDirectory(backupDirectoryUri);
            requireContext().getContentResolver()
                    .takePersistableUriPermission(backupDirectoryUri, takeFlags);

            enableBackups(requireContext());

            Navigation.findNavController(requireView())
                    .navigate(GoogleDriveRestoreFragmentDirections.actionRestore());
        }
    }

    @RequiresApi(29)
    private void displayConfirmationDialog(@NonNull Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.RestoreBackupFragment__restore_complete)
                .setMessage(R.string.RestoreBackupFragment__to_continue_using_backups_please_choose_a_folder)
                .setPositiveButton(R.string.RestoreBackupFragment__choose_folder, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION       |
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivityForResult(intent, OPEN_DOCUMENT_TREE_RESULT_CODE);
                })
                .setNegativeButton(R.string.RestoreBackupFragment__not_now, (dialog, which) -> {
                    BackupPassphrase.set(context, null);
                    dialog.dismiss();

                    Navigation.findNavController(requireView())
                            .navigate(GoogleDriveRestoreFragmentDirections.actionRestore());
                })
                .setCancelable(false)
                .show();
    }

    private void enableBackups(@NonNull Context context) {
        if (BackupUtil.canUserAccessBackupDirectory(context)) {
            LocalBackupListener.setNextBackupTimeToIntervalFromNow(context);
            TextSecurePreferences.setBackupEnabled(context, true);
            LocalBackupListener.schedule(context);
        }
    }

     private void setLoading() {
        setSpinning(restoreButton);
        skipRestoreButton.setVisibility(View.INVISIBLE);
     }

    private void cancelLoading() {
//        cancelSpinning(restoreButton);
        restoreButton.setProgress(0);
        restoreButton.setIndeterminateProgressMode(false);
        restoreButton.setClickable(true);
        skipRestoreButton.setVisibility(View.VISIBLE);
    }

    // ******** END OF COMMON METHODS ********

    @RequiresApi(24)
    private void initialiseView() {
        setLoading();
        serviceHelper.queryFilesSync()
                .addOnSuccessListener(files -> {
                    cancelLoading();
                    List<File> fileList = files.getFiles();
                    if (fileList.size() > 0) {
                        File latestFile = files.getFiles().get(0);
                        latestBackup = new Pair<>(latestFile.getId(), latestFile.getName());
                        Log.d(TAG, "Got File" + latestBackup.second);
                        restoreBackupSize.setText(getString(R.string.RegistrationActivity_backup_size_s, Util.getPrettyFileSize(latestFile.getSize())));
                        restoreBackupTime.setText(getString(R.string.RegistrationActivity_backup_timestamp_s, DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), latestFile.getModifiedTime().getValue())));
                    } else {
//                        restoreButton.setVisibility(View.INVISIBLE);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            restoreBackupTitle.setText(getString(R.string.GoogleDriveSignInFragment__google_drive_restore_backup_not_found));
                            skipRestoreButton.setVisibility(View.INVISIBLE);
                            restoreButton.setOnClickListener(v -> Navigation.findNavController(requireView()).navigate(GoogleDriveRestoreFragmentDirections.actionSkip()));
                            restoreButton.setText(getString(R.string.RegistrationActivity_next));
                        }, 500);
//                        Navigation.findNavController(requireView()).navigate(GoogleDriveRestoreFragmentDirections.actionSkip());
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    cancelLoading();
                });
    }
}

