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
package org.thoughtcrime.securesms.util;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;

import org.thoughtcrime.securesms.util.BackupUtil.BackupInfo;

import java.io.File;
import java.util.Objects;

/**
 * Utility class used which provides methods for
 */
public class DriveRestoreUtil {
    private static BackupInfo getBackupInfoFromFile(File file) {
        DocumentFile docFile = DocumentFile.fromFile(file);
        long docFileTimeStamp = BackupUtil.getBackupTimestamp(Objects.requireNonNull(docFile.getName(), "Drive file couldn't be converted to DocumentFile"));
        return new BackupInfo(docFileTimeStamp, docFile.length(), docFile.getUri());
    }

    public interface SyncCallback {
        void call (@Nullable BackupInfo backupInfo);
    }

    @RequiresApi(24)
    public static void getBackupSync(Pair<String, String> file, SyncCallback callback) {
        GoogleDriveServiceHelper.getServiceHelper().readFileSync(file.first, file.second)
                .addOnSuccessListener(backupFile -> {
                    BackupInfo backupInfoFile = null;
                    if (backupFile != null) {
                        backupInfoFile = getBackupInfoFromFile(backupFile);
                    }
                    callback.call(backupInfoFile);
                })
                .addOnFailureListener(Throwable::printStackTrace);
    }
}
