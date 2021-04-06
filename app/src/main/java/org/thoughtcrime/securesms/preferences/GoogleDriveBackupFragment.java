package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.dd.CircularProgressButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.FullBackupBase;
import org.thoughtcrime.securesms.jobs.GoogleDriveBackupJob;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.GoogleDriveServiceHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class GoogleDriveBackupFragment extends Fragment {
    private static final String TAG = Log.tag(GoogleDriveBackupFragment.class);

    private static final int REQUEST_CODE_SIGN_IN = 1;
    private static final int REQUEST_CODE_COMPLETE_AUTHORIZATION = 3;

    public static CircularProgressButton signInButton;
    public static CircularProgressButton backupButton;

    private LinearLayout backupContainer;

    private GoogleDriveServiceHelper serviceHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_google_drive_backup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        signInButton        = view.findViewById(R.id.drive_sign_in_backup);
        backupButton        = view.findViewById(R.id.drive_backup);
        backupContainer     = view.findViewById(R.id.drive_backup_container);
        backupButton.setOnClickListener(unused -> onBackupClicked());
        signInButton.setOnClickListener(unused -> requestSignIn());

//        EventBus.getDefault().register(this);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onResume() {
        super.onResume();
        toggleDriveBackup(TextSecurePreferences.isBackupEnabled(requireContext()));
//        setBackupStatus();
//        setBackupSummary();
//        setInfo();
    }

    public void toggleDriveBackup(boolean isBackupEnabled) {
        if (backupContainer == null) return;
        if (isBackupEnabled) {
            backupContainer.setVisibility(View.VISIBLE);
        } else {
            backupContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

//        EventBus.getDefault().unregister(this);
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEvent() {
////        if (TextSecurePreferences.isBackupEnabled(requireContext())) {
////            requireView().findViewById(R.id.google_drive_backup_container).setVisibility(View.VISIBLE);
////        } else {
////            requireView().findViewById(R.id.google_drive_backup_container).setVisibility(View.GONE);
////        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    @RequiresApi(24)
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    handleSignInResult(data);
                }
                break;
            case REQUEST_CODE_COMPLETE_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    Dialogs.showInfoDialog(this.getContext(), "Sign in Success", "Sign in successful!");
                } else {
                    Dialogs.showAlertDialog(this.getContext(), "Sign in Failure", "Please try signing in again!");
//                    requestSignIn();
                }
            default:
                Toast.makeText(requireContext(), R.string.GoogleDriveBackupFragment__google_drive_backup_sign_in_fail, Toast.LENGTH_LONG).show();
                Log.w(TAG, "Google Sign In failed");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public static void setSpinning() {
        if (backupButton != null) {
            backupButton.setClickable(false);
            backupButton.setIndeterminateProgressMode(true);
            backupButton.setProgress(50);
        }
    }

    public static void cancelSpinning() {
        if (backupButton != null) {
            backupButton.setProgress(0);
            backupButton.setIndeterminateProgressMode(false);
            backupButton.setClickable(true);
        }
    }

    @RequiresApi(29)
    private void onBackupClickedApi29() {
        Log.i(TAG, "Queuing drive backup...");
        GoogleDriveBackupJob.enqueue(true, serviceHelper, requireContext());
    }

    private void onBackupClickedLegacy() {
        Permissions.with(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .ifNecessary()
                .onAllGranted(() -> {
                    Log.i(TAG, "Queuing drive backup...");
                    GoogleDriveBackupJob.enqueue(true, serviceHelper, requireContext());
                })
                .withPermanentDenialDialog(getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
                .execute();
    }

    private void onBackupClicked() {
        if (BackupUtil.isUserSelectionRequired(requireContext())) {
            onBackupClickedApi29();
        } else {
            onBackupClickedLegacy();
        }
    }

    /**
     * Updates the Google Drive Button based on account details
     */
    @RequiresApi(24)
    private void updateInfo() {
        backupButton.setVisibility(View.VISIBLE);
        signInButton.setVisibility(View.GONE);
    }

    /**
     * Starts a sign-in activity using {@link #REQUEST_CODE_SIGN_IN}.
     */
    private void requestSignIn() {
        Log.d(TAG, "Requesting sign-in");

        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(requireActivity(), signInOptions);

        // The result of the sign-in Intent is handled in onActivityResult.
        startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    /**
     * Handles the {@code result} of a completed sign-in activity initiated from {@link
     * #requestSignIn()}.
     */
    @RequiresApi(24)
    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    Log.d(TAG, "Signed in successfully as: " + googleAccount.getEmail());
                    // Use the authenticated account to sign in to the Drive service.
                    GoogleAccountCredential credential =
                            GoogleAccountCredential.usingOAuth2(
                                    getActivity(), Collections.singleton(DriveScopes.DRIVE_FILE)
                            );
                    credential.setSelectedAccount(googleAccount.getAccount());
                    Log.d(TAG, "Account Set with name: " + credential.getSelectedAccount().name);
                    HttpTransport transport = AndroidHttp.newCompatibleTransport();
                    Drive googleDriveService =
                            new Drive.Builder(
                                    transport,
                                    new GsonFactory(),
                                    credential)
                                    .setApplicationName("Signal")
                                    .build();

                    // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                    // Its instantiation is required before handling any onClick actions.
                    serviceHelper = new GoogleDriveServiceHelper(googleDriveService);
                    updateInfo();
                    Toast.makeText(requireContext(), R.string.GoogleDriveBackupFragment__google_drive_backup_sign_in_success, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(exception -> Log.e(TAG, "Unable to sign in.", exception));
    }
}
