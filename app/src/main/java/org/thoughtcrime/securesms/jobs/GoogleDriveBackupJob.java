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
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class GoogleDriveBackupJob extends BaseJob {
    private static final String TAG                     = Log.tag(GoogleDriveBackupJob.class);
    public static final String KEY                      = "GoogleDriveBackupJob";

    private static final String QUEUE                   = "__GOOGLE_DRIVE_BACKUP__";

    public static final String TEMP_BACKUP_FILE_PREFIX  = ".backup";
    public static final String TEMP_BACKUP_FILE_SUFFIX  = ".tmp";

    private static Context fragmentContext;

    private GoogleDriveBackupJob(@NonNull Job.Parameters parameters) {
        super(parameters);
    }

    public static void enqueue(boolean force, @NonNull Context context) {
        GoogleDriveBackupFragment.setSpinning();
        fragmentContext                 = context;
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
                    InputStream stream = context.getContentResolver().openInputStream(temporaryFile.getUri());

                    OutputStream buffer = new ByteArrayOutputStream();

                    int nRead;
                    byte[] data = new byte[16384];
//
                    while ((nRead = stream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }

                    stream.close();

                    GoogleDriveServiceHelper.serviceHelper.queryFilesSync()
                            .addOnSuccessListener(fileList -> {
                                fileList.getFiles().forEach(file -> {
                                    GoogleDriveServiceHelper.serviceHelper.deleteFileSync(file.getId());
                                });
                            });

                    GoogleDriveServiceHelper.serviceHelper.createFile(fileName, buffer)
                            .addOnSuccessListener(id -> {
                                Log.i(TAG, "Successfully uploaded backup to google drive");
                                Log.i(TAG, "Deleting locally created file now... " + temporaryFile.delete());
                                Toast.makeText(fragmentContext, R.string.GoogleDriveBackupFragment__google_drive_backup_success, Toast.LENGTH_LONG).show();
                                GoogleDriveBackupFragment.cancelSpinning();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(fragmentContext, R.string.GoogleDriveBackupFragment__google_drive_backup_fail, Toast.LENGTH_LONG).show();
                                Log.e(TAG, e);
                                e.printStackTrace();
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

            BackupUtil.deleteOldBackups();
        }
    }


    // TODO: Delete temporary old/temp backups from Google Drive as well.
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
