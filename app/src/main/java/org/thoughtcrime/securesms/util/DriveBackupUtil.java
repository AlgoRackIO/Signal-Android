package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
//import com.google.api.services.drive.Drive;
//import com.google.api.services.drive.model.FileList;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.BackupUtil.BackupInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

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
    private static <T> CompletableFuture<T> getCompleteableFuture(T data) {
        return CompletableFuture.supplyAsync(() -> data);
    }

    @RequiresApi(24)
    public static CompletableFuture<BackupInfo> getLatestBackup(GoogleDriveServiceHelper driveService) {
        try {
            FileList fileList = driveService.queryFiles().get();
            if (fileList != null) {
                List<com.google.api.services.drive.model.File> files = fileList.getFiles();
                if (files.size() == 0) return getCompleteableFuture(null);
                try {
                    String latestBackupId = files.get(0).getId();
                    File backupFile = driveService.readFile(latestBackupId).get();
                    if (backupFile != null) {
                        return getCompleteableFuture(getBackupInfoFromFile(backupFile));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return getCompleteableFuture(null);
    }
}
