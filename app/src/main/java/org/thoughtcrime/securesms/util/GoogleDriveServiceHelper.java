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

import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.signal.core.util.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility class for performing read/write operations through helper methods on Drive files via the REST API
 * Uses google drive sdk internally to perform actions.
 */

// TODO: Create Service factory for singleton
public class GoogleDriveServiceHelper {
    private final String TAG = Log.tag(GoogleDriveServiceHelper.class);
    private final String ACCESS_SPACE = "appDataFolder";

    private final Executor  executor     = Executors.newSingleThreadExecutor();
    private final Drive     driveService;

    private static GoogleDriveServiceHelper serviceHelper;

    private GoogleDriveServiceHelper(Drive driveService) {
        this.driveService   = driveService;
    }

    public static GoogleDriveServiceHelper createServiceHelper(Drive service) {
        if (serviceHelper == null) {
            serviceHelper = new GoogleDriveServiceHelper(service);
        }
        return serviceHelper;
    }
    /**
     * Creates a new {@link OutputStream} from the provided {@link InputStream}
     * returns {@link OutputStream} the {@link InputStream} was written to
     * @throws IOException
     */
    private OutputStream writeToOutputStream(InputStream stream) throws IOException {
        OutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        stream.close();
        return buffer;
    }

    /**
     * Creates the specified file in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFile(String name, InputStream inputStream) {
        return Tasks.call(executor, () -> {
            File metadata   = new File()
                        .setMimeType("application/octet-stream")
                        .setName(name)
                        .setParents(Collections.singletonList(ACCESS_SPACE));

            OutputStream outputStream = writeToOutputStream(inputStream);
            Log.i(TAG, "Set storage space for drive: " + metadata.getParents());

            File googleFile = driveService.files().create(
                    metadata,
                    new ByteArrayContent("application/octet-stream", ((ByteArrayOutputStream) outputStream).toByteArray() )
            ).setFields("id").execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return googleFile.getId();
        });
    }

    /**
     * Deletes the file identified by {@code fileId} permanently from drive sdk and
     * returns {@link Void}
     */
    public Task<Void> deleteFileSync(String fileId) {
        return Tasks.call(executor, () -> {
            Drive.Files.Delete deleteRequest = driveService.files().delete(fileId);
            Log.i(TAG, "Deleting file with id: " + deleteRequest.getFileId());
            deleteRequest.execute();
            return null;
        });
    }

    /**
     * Call {@link GoogleDriveServiceHelper#createServiceHelper} before using the service helper
     * @return {@link GoogleDriveServiceHelper}
     */
    public static GoogleDriveServiceHelper getServiceHelper() {
        return serviceHelper;
    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
    @RequiresApi(24)
    public Task<java.io.File> readFileSync(String fileId, String name) {
        return Tasks.call(executor, () -> {
            try {
                Drive.Files.Get driveGETRequest = driveService.files().get(fileId);
                Log.d(TAG, "Requesting file from drive with ID: " + driveGETRequest.getFileId());
                String[] filename = name.split("\\.");
                java.io.File file = java.io.File.createTempFile(filename[0], "." + filename[1]);
                OutputStream outputStream = new FileOutputStream(file);
                Log.d(TAG, "Downloading file...");
                driveGETRequest.executeMediaAndDownloadTo(outputStream);
                Log.d(TAG, "File Downloaded!");
                return file;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Returns a {@link FileList} containing all the visible files in the user's My Drive.
     *
     * <p>The returned list will only contain files visible to this app, i.e. those which were
     * created by this app. To perform operations on files not created by the app, the project must
     * request Drive Full Scope in the <a href="https://play.google.com/apps/publish">Google
     * Developer's Console</a> and be submitted to Google for verification.</p>
     */
    @RequiresApi(24)
    public Task<FileList> queryFilesSync() {
        return Tasks.call(executor, () -> {
//            Drive.Files.List list = driveService.files().list().setSpaces("drive,appDataFolder").setFields("files(id,name,modifiedTime,size)");
//            Drive.Files.List list = driveService.files().list().setFields("files(id,name,modifiedTime,size)");
            Drive.Files.List list = driveService.files().list()
//                    .setSpaces(ACCESS_SPACE)
                    .setFields("nextPageToken, files(id, name)").setPageSize(10);
            list.getFields();
            return list.execute();
        });
    }
}