package org.thoughtcrime.securesms.util;

import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.services.drive.model.FileList;
//import com.google.api.services.drive.Drive;
//import com.google.api.services.drive.model.FileList;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.BackupUtil.BackupInfo;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class DriveBackupUtil {
    private static final String TAG = Log.tag(DriveBackupUtil.class);

    public interface OnGetBackup {
        void onBackupFetchComplete(BackupInfo file);
    }

    private static BackupInfo getBackupInfoFromFile(File file) {
        DocumentFile docFile = DocumentFile.fromFile(file);
        long docFileTimeStamp = BackupUtil.getBackupTimestamp(Objects.requireNonNull(docFile.getName(), "Drive file couldn't be converted to DocumentFile"));
        return new BackupInfo(docFileTimeStamp, docFile.length(), docFile.getUri());
    }

    @RequiresApi(24)
    private static <T> CompletableFuture<T> getCompletableFuture(T data) {
        return CompletableFuture.supplyAsync(() -> data);
    }

    public interface SyncCallback {
        void run (@Nullable BackupInfo backupInfo);
    }

    @RequiresApi(24)
    public static void getBackupSync(GoogleDriveServiceHelper driveService, Pair<String, String> file, SyncCallback callback) {
        driveService.readFileSync(file.first, file.second)
                .addOnSuccessListener(backupFile -> {
                    BackupInfo backupInfoFile = null;
                    if (backupFile != null) {
                        backupInfoFile = getBackupInfoFromFile(backupFile);
                    }
                    callback.run(backupInfoFile);
                })
                .addOnFailureListener(Throwable::printStackTrace);
    }

    @RequiresApi(24)
    public static CompletableFuture<BackupInfo> getBackup(GoogleDriveServiceHelper driveService, Pair<String, String> file) {
        BackupInfo backupInfoFile = null;
        try {
            File backupFile = driveService.readFile(file.first, file.second).get();
            if (backupFile != null) {
                backupInfoFile = getBackupInfoFromFile(backupFile);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return getCompletableFuture(backupInfoFile);
    }

    @RequiresApi(24)
    public static CompletableFuture<BackupInfo> getLatestBackup(GoogleDriveServiceHelper driveService) {
        try {
            FileList fileList = driveService.queryFiles().get();
            if (fileList != null) {
                List<com.google.api.services.drive.model.File> files = fileList.getFiles();
                if (files.size() == 0) return getCompletableFuture(null);
                try {
                    com.google.api.services.drive.model.File latestBackup = files.get(0);
                    File backupFile = driveService.readFile(latestBackup.getId(), latestBackup.getName()).get();
                    if (backupFile != null) {
                        return getCompletableFuture(getBackupInfoFromFile(backupFile));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return getCompletableFuture(null);
    }
}
