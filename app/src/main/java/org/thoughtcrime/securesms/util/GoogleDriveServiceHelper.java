package org.thoughtcrime.securesms.util;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.FileUtils;
import android.provider.OpenableColumns;

import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.signal.core.util.logging.Log;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */

// TODO: Create Service factory for singleton
public class GoogleDriveServiceHelper {
    private final String TAG = Log.tag(GoogleDriveServiceHelper.class);

    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive driveService;
//    private final String APP_FOLDER = "appDataFolder";
    private final String APP_FOLDER = "drive";
    public static GoogleDriveServiceHelper serviceHelper;

    public GoogleDriveServiceHelper(Drive driveService) {
        this.driveService   = driveService;
        serviceHelper       = this; // TODO: Temporary code to test
    }

    /**
     * Creates a text file in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFile() {
        return Tasks.call(mExecutor, () -> {
            File metadata = new File()
                    .setParents(Collections.singletonList("root"))
                    .setMimeType("text/plain")
                    .setName("Untitled file");

            File googleFile = driveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            String fileId = googleFile.getId();

            Log.d(TAG, "File successfully created with ID: " + fileId);

            return fileId;
        });
    }

//    public <T> AsyncTask<String, Integer, T> executeAsync(Callbac) {
//        return (AsyncTask<String, Integer, T>) new AsyncTask<String, Integer, Long>() {
//            @Override
//            protected Long doInBackground(String... strings) {
//                return null;
//            }
//
//
//        };
//    }

    /**
     * Creates the specified file in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFile(String name, OutputStream outputStream) {
        return Tasks.call(mExecutor, () -> {
//            String mime = mimeType != null ? mimeType : "application/octet-stream";
            File metadata = new File()
//                    .setParents(Collections.singletonList("root"))
//                    .setParents(Collections.singletonList(APP_FOLDER))
                    .setMimeType("application/octet-stream")
                    .setName(name);

//            ByteArrayContent contentStream = ByteArrayContent.fromString("application/octet-stream", content);

                File googleFile = driveService.files().create(
                    metadata,
//                    mediaContent
                    new ByteArrayContent("application/octet-stream", ((ByteArrayOutputStream) outputStream).toByteArray() )
            ).setFields("id").execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return googleFile.getId();
        });
    }

    public void writeStreamToFile(InputStream input, java.io.File file) {
        try {
            try (OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024]; // or other buffer size
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

//    public Task<Pair<String, String>> readFile(String fileId) {
//        return Tasks.call(mExecutor, () -> {
//            // Retrieve the metadata as a File object.
////            File metadata = driveService.files().get(fileId).execute();
////            String name = metadata.getName();
//
//            // Stream the file contents to a String.
//            try (
////                    InputStream is = driveService.files().get(fileId).executeMediaAsInputStream();
//                 BufferedReader reader = new BufferedReader(new InputStreamReader(new InputStream() {
//                     @Override
//                     public int read() throws IOException {
//                         return 0;
//                     }
//                 }))) {
//                StringBuilder stringBuilder = new StringBuilder();
//                String line;
//
//                while ((line = reader.readLine()) != null) {
//                    stringBuilder.append(line);
//                }
//                String contents = stringBuilder.toString();
//
//                return Pair.create("name", contents);
//            }
//        });
//    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link android.util.Pair} of its name and
     * contents.
     */
//    public Task<android.util.Pair<String, String>> readFile(String fileId, String test) {
//        return Tasks.call(mExecutor, () -> {
//            // Retrieve the metadata as a File object.
//            File metadata = driveService.files().get(fileId).execute();
////            driveService.files().list()
//            String name = metadata.getName();
//
////            driveService.files().get(fileId).setOauthToken(OAuthToken).setFileId(fileId).buildHttpRequestUsingHead().execute().getContent();
//
//            // Stream the file contents to a String.
//            try (InputStream is = driveService.files().get(fileId).setOauthToken(OAuthToken).setFileId(fileId).executeMediaAsInputStream();
//                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
//                StringBuilder stringBuilder = new StringBuilder();
//                String line;
//
//                while ((line = reader.readLine()) != null) {
//                    stringBuilder.append(line);
//                }
//                String contents = stringBuilder.toString();
//
//                return android.util.Pair.create(name, contents);
//            }
//        });
//    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
//    public Task<java.io.File> readAndWriteToFile(String fileId, java.io.File file) {
//        return Tasks.call(mExecutor, () -> {
//            // Retrieve the metadata as a File object.
//            File metadata = driveService.files().get(fileId).execute();
//            List<com.google.api.services.drive.model.Permission> list = driveService.permissions().list(fileId).execute().getPermissions();
//            for (com.google.api.services.drive.model.Permission perm : list) {
//                Log.i(TAG, perm.getDisplayName());
//            }
//            Log.i(TAG, "File name: " + metadata.getName());
//            File.Capabilities capabilities = metadata.getCapabilities();
//            if (capabilities != null) {
//                Log.i(TAG, "Can Download File: " + capabilities.getCanDownload());
//            }
////            Log.i(TAG, "Can Download File: " + capabilities.setCanDownload(true));
////            driveService.files().update(fileId, metadata).execute();
////            metadata.setParents(Collections.singletonList(APP_FOLDER));
////            String name = metadata.getName();
//            try {
////                InputStream is = driveService.files().get(fileId).setAlt("media").executeMediaAsInputStream();
//                OutputStream os = new ByteArrayOutputStream();
//                HttpResponse response = driveService.files().get(fileId).executeMedia();
//                response.download(os);
////                writeStreamToFile(is, file);
//            } catch (IOException err) {
//                Log.e(TAG, err);
//            }
//
////            return Pair.create(file);
//            return file;
//            // Stream the file contents to a String.
////            try (InputStream is = driveService.files().get(fileId).executeMediaAsInputStream();
////                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
////                StringBuilder stringBuilder = new StringBuilder();
////                String line;
////
////                while ((line = reader.readLine()) != null) {
////                    stringBuilder.append(line);
////                }
////                String contents = stringBuilder.toString();
////
////                return Pair.create(name, contents);
////            }
//        });
//    }

//    public String getAppFolder() {
//        return APP_FOLDER;
//    }

    @RequiresApi(24)
    public Task<java.io.File> readFileSync(String fileId, String name) {
        return Tasks.call(mExecutor, () -> {
            try {
                Drive.Files.Get driveGETRequest = driveService.files().get(fileId);
                Log.d(TAG, "Requesting file from drive with ID: " + driveGETRequest.getFileId());
//                driveGETRequest.setFields("files(id, name)");
//                Log.d(TAG, "Set Drive Get Fields: " + driveGETRequest.getFields());
//                Log.d(TAG, "Requesting Metadata");
//                File metadata = driveGETRequest.execute();
//                Log.d(TAG, "Metadata received");
//                String[] filename = metadata.getName().split("\\.");
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
//    public Task<Pair<String, String>> readFile(String fileId) {
//    public Task<InputStream> readFile(String fileId) {
//    public Task<OutputStream> readFile(String fileId) {
//    public Task<java.io.File> readFile(String fileId) {
    @RequiresApi(24)
    public CompletableFuture<java.io.File> readFile(String fileId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Drive.Files.Get driveGETRequest = driveService.files().get(fileId);
                Log.d(TAG, "Requesting file from drive with ID: " + driveGETRequest.getFileId());
//                driveGETRequest.setFields("files(id, name)");
//                Log.d(TAG, "Set Drive Get Fields: " + driveGETRequest.getFields());
//                Log.d(TAG, "Requesting Metadata");
//                File metadata = driveGETRequest.execute();
//                Log.d(TAG, "Metadata received");
//                String[] filename = metadata.getName().split("\\.");
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
//        return Tasks.call(mExecutor, () -> {
//            if (true) return future.get();
//            // MANUAL HTTP REQUEST
//
////            GenericUrl url = driveService.files().get(fileId).setOauthToken(OAuthToken)
//
//
//            // Retrieve the metadata as a File object.
////            File metadata = driveService.files().get(fileId).set("mimeType", "application/octet-stream").execute();
////            driveService.files().get(fileId).set("mimeType", "application/octet-stream")
////            metadata.setParents(Collections.singletonList(APP_FOLDER));
////            String name = metadata.getName();
//            // Stream the file contents to a String.
////            java.io.File file = java.io.File.createTempFile("signal-", ".backup");
////            OutputStream outputStream = new FileOutputStream(file.getPath());
////            OutputStream outputStream = new ByteArrayOutputStream();
////            GenericUrl url =            driveService.files().get(fileId).buildHttpRequestUrl();
////            String oauth =              driveService.files().list().getOauthToken();
////            HttpHeaders headers =       driveService.files().get(fileId).getRequestHeaders();
////            GenericUrl url = driveService.files().get(fileId).buildHttpRequestUrl();
////            driveService.files().get(fileId).buildHttpRequestUrl().appendRawPath(fileId);
////            driveService.files().get(fileId).setAlt("media").setFileId(fileId).executeMediaAndDownloadTo(outputStream);
////            driveService.files().get(fileId).setFileId(fileId)
//            Drive.Files.Get driveGETRequest = driveService.files().get(fileId);
//            Log.d(TAG, "Requesting file from drive with ID: " + driveGETRequest.getFileId());
//            File metadata = driveGETRequest.execute();
//            String[] filename = metadata.getName().split("\\.");
//            java.io.File file = java.io.File.createTempFile(filename[0], "." + filename[1]);
//            OutputStream outputStream = new FileOutputStream(file);
//            driveGETRequest.executeMediaAndDownloadTo(outputStream);
//
////            File metadata = driveGETRequest.execute();
////            String name = metadata.getName();
//
//            // Stream the file contents to a String.
////            return Pair.create(name, "Hello world");
//            return file;
//////                    .setOauthToken()
////                    .setFields("id").executeMediaAndDownloadTo(outputStream);
////            return Pair.create(name, outputStream.toString());
////            driveService.files().get(fileId).executeMediaAsInputStream();
////            HttpRequest request = driveService.files().get(fileId).setOauthToken(OAuthToken).setFileId(fileId).setAlt("media").buildHttpRequestUsingHead();
////            HttpHeaders headers = request.getHeaders();
////            GenericUrl url = request.getUrl();
////            driveService.files().get(fileId).setOauthToken(OAuthToken).setFileId(fileId).setAlt("media").buildHttpRequestUsingHead().getHeaders();
////            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
////            driveService.files().get(fileId)
////                    .setOauthToken(OAuthToken)
////                    .setFileId(fileId)
////                    .setAlt("media")
////                    .buildHttpRequestUsingHead()
////                    .execute()
////                    .download(outputStream);
////            driveService.files().get(fileId).setOauthToken(OAuthToken).setFileId(fileId).executeMediaAndDownloadTo(outputStream);
////            try (InputStream is = driveService.files().get(fileId).executeMediaAsInputStream()) {
////            try (InputStream is = driveService.files().export(fileId, "application/octet-stream").executeMediaAsInputStream();
////                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
////            InputStream is = driveService.files().export(fileId, "application/octet-stream").executeMediaAsInputStream();
////            InputStream is = driveService.files().get(fileId).set("mimeType", "application/octet-stream").executeMediaAsInputStream();
////            InputStream is = driveService.files().get(fileId).executeMediaAsInputStream();
////                StringBuilder stringBuilder = new StringBuilder();
////                String line;
////                reader.close();
////
////                while ((line = reader.readLine()) != null) {
////                    stringBuilder.append(line);
////                }
////                String contents = stringBuilder.toString();
////
////                return Pair.create(name, contents);
////                 return is;
////            }
////            return outputStream;
//        });
    }


//    public Task<Pair<List<File>, String>> searchFiles(@Nullable String page) {
//        return Tasks.call(mExecutor, () -> {
//            FileList result = driveService.files().list()
//                    .setSpaces(APP_FOLDER)
//                    .setFields("nextPageToken, files(id, name)")
//                    .setPageSize(10)
//                    .setPageToken(page)
//                    .execute();
//            List<File> files = result.getFiles();
//            for (File file : files) {
//                Log.d(TAG, "Found file: " + file.getName() + " :: id: " + file.getId());
//            }
//            return Pair.create(files, page);
//        });
//    }

    /**
     * Updates the file identified by {@code fileId} with the given {@code name} and {@code
     * content}.
     */
//    public Task<Void> saveFile(String fileId, String name, String content) {
//        return Tasks.call(mExecutor, () -> {
//            // Create a File containing any metadata changes.
//            File metadata = new File().setName(name);
//
//            // Convert content to an AbstractInputStreamContent instance.
//            ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);
//
//            // Update the metadata and contents.
//            driveService.files().update(fileId, metadata, contentStream).execute();
//            return null;
//        });
//    }

    /**
     * Updates the file identified by {@code fileId} with the given {@code name} and {@code
     * content}.
     */
//    public Task<Void> storeBackup(String fileId, String name, String content) {
//        return Tasks.call(mExecutor, () -> {
//            // Create a File containing any metadata changes.
////            File metadata = new File().setName(name);
//
//            // Convert content to an AbstractInputStreamContent instance.
//            ByteArrayContent contentStream = ByteArrayContent.fromString("application/octet-stream", content);
//
//            // Update the metadata and contents.
////            driveService.files().update(fileId, metadata, contentStream).execute();
//            return null;
//        });
//    }

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
//                Drive.Files.List list = driveService.files().list()
//                        .setFields("files(id,name,modifiedTime,size)")
//                        .setSpaces(APP_FOLDER);
                Drive.Files.List list = driveService.files().list().setFields("files(id,name,modifiedTime,size)").setSpaces(APP_FOLDER);
//                list.getSpaces();
                return list.execute();
//                return fileList;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }, mExecutor);
    }

    @RequiresApi(24)
    public Task<FileList> queryFilesSync() {
        return Tasks.call(mExecutor, () -> {
            Drive.Files.List list = driveService.files().list().setFields("files(id,name,modifiedTime,size)").setSpaces("drive");
            list.getFields();
            return list.execute();
        });
    }

    /**
     * Returns an {@link Intent} for opening the Storage Access Framework file picker.
     */
    public Intent createFilePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");

        return intent;
    }

//    public <T> Task<T> executeWithDrive(DriveCallback<T> params) {
//        return Tasks.call(mExecutor, () -> params.callback(driveService));
//    }
//
//    /**
//     * Returns an {@link Drive} for accessing sdk methods.
//     */
//    public Drive getDriveService() throws IllegalAccessException {
//        if (driveService == null) {
//            throw new IllegalAccessException("Drive service was not created!");
//        }
//        return driveService;
//    }

    /**
     * Opens the file at the {@code uri} returned by a Storage Access Framework {@link Intent}
     * created by {@link #createFilePickerIntent()} using the given {@code contentResolver}.
     */
    public Task<Pair<String, String>> openFileUsingStorageAccessFramework(
            ContentResolver contentResolver, Uri uri) {
        return Tasks.call(mExecutor, () -> {
            // Retrieve the document's display name from its metadata.
            String name;
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    name = cursor.getString(nameIndex);
                } else {
                    throw new IOException("Empty cursor returned for file.");
                }
            }

            // Read the document's contents as a String.
            String content;
            try (InputStream is = contentResolver.openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                content = stringBuilder.toString();
            }

            return Pair.create(name, content);
        });
    }
}