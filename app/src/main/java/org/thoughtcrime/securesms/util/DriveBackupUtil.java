package org.thoughtcrime.securesms.util;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;

import org.thoughtcrime.securesms.util.BackupUtil.BackupInfo;

import java.io.File;
import java.util.Objects;

public class DriveBackupUtil {
    private static BackupInfo getBackupInfoFromFile(File file) {
        DocumentFile docFile = DocumentFile.fromFile(file);
        long docFileTimeStamp = BackupUtil.getBackupTimestamp(Objects.requireNonNull(docFile.getName(), "Drive file couldn't be converted to DocumentFile"));
        return new BackupInfo(docFileTimeStamp, docFile.length(), docFile.getUri());
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
}
