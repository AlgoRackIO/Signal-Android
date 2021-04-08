package org.thoughtcrime.securesms.registration.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

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

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.GoogleDriveServiceHelper;

import java.util.Collections;

public class GoogleDriveSignInFragment extends Fragment {
    private static final String TAG = Log.tag(GoogleDriveSignInFragment.class);
    private GoogleDriveServiceHelper serviceHelper;

    private static final int REQUEST_CODE_SIGN_IN = 1;
//    private static final int REQUEST_CODE_OPEN_DOCUMENT = 2;
    private static final int REQUEST_CODE_COMPLETE_AUTHORIZATION = 3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration_google_drive_restore_signin, container, false);
//        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.google_drive_sign_in_button).setOnClickListener(unused -> requestSignIn());
        view.findViewById(R.id.skip_drive_restore).setOnClickListener(unused -> Navigation.findNavController(requireView()).navigate(GoogleDriveSignInFragmentDirections.actionSkip()));
    }

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
                    requestSignIn();
                }
            default:
                Log.d(TAG, "Google Sign In exit");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    Log.d(TAG, "Signed in successfully as: " + googleAccount.getEmail());

//                    setButtonsVisibility();
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
//                                    new NetHttpTransport(),
//                                    AndroidHttp.newCompatibleTransport(),
                                    transport,

//                                    new ApacheHttpTransport(),
                                    new GsonFactory(),
                                    credential)
                                    .setApplicationName("Signal")
                                    .build();

                    // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                    // Its instantiation is required before handling any onClick actions.
                    serviceHelper = GoogleDriveServiceHelper.createServiceHelper(googleDriveService);
                    Navigation.findNavController(requireView()).navigate(GoogleDriveSignInFragmentDirections.actionRestoreFromDrive());
                })
                .addOnFailureListener(exception -> Log.e(TAG, "Unable to sign in.", exception));
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
}
