///**
// * Copyright (C) 2011 Whisper Systems
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
package org.thoughtcrime.securesms.jobs;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import com.google.api.services.drive.model.File;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupFileIOError;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupExporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.ChargingConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.preferences.GoogleDriveBackupFragment;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.GoogleDriveServiceHelper;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Mostly re-uses code from {@link LocalBackupJob} which has been modified to create
 * encrypted backup and upload it to Google Drive Storage
 */
public class GoogleDriveBackupJob extends BaseJob {
    private static final String TAG                     = Log.tag(GoogleDriveBackupJob.class);
    public static final String KEY                      = "GoogleDriveBackupJob";

    private static final String QUEUE                   = "__GOOGLE_DRIVE_BACKUP__";

    public static final String TEMP_BACKUP_FILE_PREFIX  = ".backup";
    public static final String TEMP_BACKUP_FILE_SUFFIX  = ".tmp";

    private GoogleDriveBackupJob(@NonNull Job.Parameters parameters) {
        super(parameters);
    }

    public static void enqueue(boolean force) {
        GoogleDriveBackupFragment.setSpinning();
        JobManager jobManager           = ApplicationDependencies.getJobManager();
        Parameters.Builder parameters   = new Parameters.Builder()
                .setQueue(QUEUE)
                .setMaxInstancesForFactory(1)
                .setMaxAttempts(3);
        if (force) {
            jobManager.cancelAllInQueue(QUEUE);
        } else {
            parameters.addConstraint(ChargingConstraint.KEY);
        }
        jobManager.add(new GoogleDriveBackupJob(parameters.build()));
    }

    @SuppressLint("ShowToast")
    @Override
    protected void onRun() throws Exception {
        Log.i(TAG, "Executing backup job...");

        BackupFileIOError.clearNotification(context);

         if (!BackupUtil.isUserSelectionRequired(context)) {
            throw new IOException("Wrong backup job!");
        }

        Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
        if (backupDirectoryUri == null || backupDirectoryUri.getPath() == null) {
            throw new IOException("Backup Directory has not been selected!");
        }

        try (NotificationController notification = GenericForegroundService.startForegroundTask(context,
                context.getString(R.string.LocalBackupJob_creating_backup),
                NotificationChannels.BACKUPS,
                R.drawable.ic_signal_backup))
        {
            notification.setIndeterminateProgress();

            String       backupPassword  = BackupPassphrase.get(context);
            DocumentFile backupDirectory = DocumentFile.fromTreeUri(context, backupDirectoryUri);
            String       timestamp       = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
            String       fileName        = String.format("signal-%s.backup", timestamp);

            if (backupDirectory == null || !backupDirectory.canWrite()) {
                BackupFileIOError.ACCESS_ERROR.postNotification(context);
                throw new IOException("Cannot write to backup directory location.");
            }

            deleteOldTemporaryBackups(backupDirectory);

            if (backupDirectory.findFile(fileName) != null) {
                throw new IOException("Backup file already exists!");
            }

            String       temporaryName = String.format(Locale.US, "%s%s%s", TEMP_BACKUP_FILE_PREFIX, UUID.randomUUID(), TEMP_BACKUP_FILE_SUFFIX);
            DocumentFile temporaryFile = backupDirectory.createFile("application/octet-stream", temporaryName);

            if (temporaryFile == null) {
                throw new IOException("Failed to create temporary backup file.");
            }

            if (backupPassword == null) {
                throw new IOException("Backup password is null");
            }

            try {
                FullBackupExporter.export(context,
                        AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                        DatabaseFactory.getBackupDatabase(context),
                        temporaryFile,
                        backupPassword);

                try {
                    // Open stream to temporary file to upload it to Drive Storage
                    InputStream tempFileStream = context.getContentResolver().openInputStream(temporaryFile.getUri());

                    GoogleDriveServiceHelper serviceHelper = GoogleDriveServiceHelper.getServiceHelper();

                    deleteOldDriveBackups(serviceHelper);

                    serviceHelper.createFile(fileName, tempFileStream)
                            .addOnSuccessListener(id -> {
                                Log.i(TAG, "Successfully uploaded backup to google drive");
                                Toast.makeText(context, R.string.GoogleDriveBackupFragment__google_drive_backup_success, Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, R.string.GoogleDriveBackupFragment__google_drive_backup_fail, Toast.LENGTH_LONG).show();
                                Log.e(TAG, e);
                                e.printStackTrace();
                            })
                            .addOnCompleteListener(unused -> {
                                Log.i(TAG, "Deleting locally created file now... " + temporaryFile.delete());
                                GoogleDriveBackupFragment.cancelSpinning();
                            });
                } catch (IOException e) {
                    Log.e(TAG, e);
                }

                if (!temporaryFile.renameTo(fileName)) {
                    Log.w(TAG, "Failed to rename temp file");
                    throw new IOException("Renaming temporary backup file failed!");
                }
            } catch (IOException e) {
                Log.w(TAG, "Error during backup!", e);
                BackupFileIOError.postNotificationForException(context, e, getRunAttempt());
                throw e;
            } finally {
                DocumentFile fileToCleanUp = backupDirectory.findFile(temporaryName);
                if (fileToCleanUp != null) {
                    if (fileToCleanUp.delete()) {
                        Log.w(TAG, "Backup failed. Deleted temp file");
                    } else {
                        Log.w(TAG, "Backup failed. Failed to delete temp file " + temporaryName);
                    }
                }
            }

//            BackupUtil.deleteOldBackups();
        }
    }

    /**
     * Deletes previous backup files uploaded to Google Drive
     * Requires {@link GoogleDriveServiceHelper} to access drive sdk and delete the corresponding files.
     */
    @RequiresApi(24)
    private static void deleteOldDriveBackups(GoogleDriveServiceHelper serviceHelper) {
        serviceHelper.queryFilesSync()
                .addOnSuccessListener(fileList -> {
                    fileList.getFiles().stream().map(File::getId).forEach(fileId -> serviceHelper.deleteFileSync(fileId)
                            .addOnSuccessListener(unused -> {
                                Log.i(TAG, "Successfully deleted file with id: " + fileId);
                            }).addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting file with id: " + fileId);
                            }));
                });
    }

    private static void deleteOldTemporaryBackups(@NonNull DocumentFile backupDirectory) {
        for (DocumentFile file : backupDirectory.listFiles()) {
            if (file.isFile()) {
                String name = file.getName();
                if (name != null && name.startsWith(TEMP_BACKUP_FILE_PREFIX) && name.endsWith(TEMP_BACKUP_FILE_SUFFIX)) {
                    if (file.delete()) {
                        Log.w(TAG, "Deleted old temporary backup file");
                    } else {
                        Log.w(TAG, "Could not delete old temporary backup file");
                    }
                }
            }
        }
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return false;
    }

    @NonNull
    @Override
    public Data serialize() {
        return Data.EMPTY;
    }

    @NonNull
    @Override
    public String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onFailure() {
    }

    public static class Factory implements Job.Factory<GoogleDriveBackupJob> {
        @Override
        public @NonNull GoogleDriveBackupJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new GoogleDriveBackupJob(parameters);
        }
    }
}
