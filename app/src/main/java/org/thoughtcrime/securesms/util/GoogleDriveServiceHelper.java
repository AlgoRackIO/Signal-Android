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
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility class for performing read/write operations through helper methods on Drive files via the REST API
 * Uses google drive sdk internally to perform actions.
 */

// TODO: Create Service factory for singleton
public class GoogleDriveServiceHelper {
    private final String TAG = Log.tag(GoogleDriveServiceHelper.class);

    private final String    APP_FOLDER     = "drive";
    private final Executor  executor     = Executors.newSingleThreadExecutor();
    private final Drive     driveService;

    public static GoogleDriveServiceHelper serviceHelper;

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
     * Creates the specified file in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFile(String name, OutputStream outputStream) {
        return Tasks.call(executor, () -> {
            File metadata   = new File()
                        .setMimeType("application/octet-stream")
                        .setName(name);

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
     * Deletes file with the corresponding fileId permanently from drive sdk
     * METHOD: DELETE | URL: https://www.googleapis.com/drive/v2/files/{fileId}
     * @param fileId
     * @return
     */
    public Task<Void> deleteFileSync(String fileId) {
        return Tasks.call(executor, () -> {
            Drive.Files.Delete deleteRequest = driveService.files().delete(fileId);
            Log.i(TAG, "Deleting file with id: " + deleteRequest.getFileId());
            deleteRequest.execute();
            return null;
        });
    }

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
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
    @RequiresApi(24)
    public CompletableFuture<java.io.File> readFile(String fileId, String name) {
        return CompletableFuture.supplyAsync(() -> {
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
    public CompletableFuture<FileList>queryFiles() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Drive.Files.List list = driveService.files().list().setFields("files(id,name,modifiedTime,size)").setSpaces(APP_FOLDER);
//                list.getSpaces();
                return list.execute();
//                return fileList;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }, executor);
    }

    @RequiresApi(24)
    public Task<FileList> queryFilesSync() {
        return Tasks.call(executor, () -> {
            Drive.Files.List list = driveService.files().list().setFields("files(id,name,modifiedTime,size)").setSpaces("drive");
            list.getFields();
            return list.execute();
        });
    }
}