/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package ml.easychad.lax.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;

import ml.easychad.lax.PhoneFormat.PhoneFormat;
import ml.easychad.lax.android.AndroidUtilities;
import ml.easychad.lax.android.LocaleController;
import ml.easychad.lax.android.NotificationCenter;
import ml.easychad.lax.android.SendMessagesHelper;
import ml.easychad.lax.android.TrialController;
import ml.easychad.lax.messenger.ConnectionsManager;
import ml.easychad.lax.messenger.FileLog;
import ml.easychad.lax.messenger.R;
import ml.easychad.lax.messenger.TLRPC;
import ml.easychad.lax.messenger.UserConfig;
import ml.easychad.lax.messenger.Utilities;
import ml.easychad.lax.ui.Views.ActionBar.ActionBarLayout;
import ml.easychad.lax.ui.Views.ActionBar.BaseFragment;
import ml.easychad.lax.util.IabHelper;
import ml.easychad.lax.util.IabResult;
import ml.easychad.lax.util.Inventory;
import ml.easychad.lax.util.Purchase;

public class LaunchActivity extends Activity implements ActionBarLayout.ActionBarLayoutDelegate, NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate {
    private boolean finished = false;
    private String videoPath = null;
    private String sendingText = null;
    private ArrayList<Uri> photoPathsArray = null;
    private ArrayList<String> documentsPathsArray = null;
    private ArrayList<String> documentsOriginalPathsArray = null;
    private ArrayList<TLRPC.User> contactsToSend = null;
    private int currentConnectionState;
    private static ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<BaseFragment>();
    private static ArrayList<BaseFragment> layerFragmentsStack = new ArrayList<BaseFragment>();
    private static ArrayList<BaseFragment> rightFragmentsStack = new ArrayList<BaseFragment>();

    private ActionBarLayout actionBarLayout = null;
    private ActionBarLayout layersActionBarLayout = null;
    private ActionBarLayout rightActionBarLayout = null;
    private FrameLayout shadowTablet = null;
    private LinearLayout buttonLayoutTablet = null;
    private FrameLayout shadowTabletSide = null;
    private ImageView backgroundTablet = null;
    private boolean tabletFullSize = false;

    String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnEUNRjDg2C9mF+xsdBz2XCqdRVfU9G/2kLkQjuBPUj7Ik5DkvjuCd0lcSL338ho0URzUChiHaC/ZtpBUk40zIEqdeI1u3UWs27Lo6Q4h9Rg11UW9Hc7EH0OyhVD5Z9k69/oY/aTRXMehA2VRH5dR7sOEc8KdGp5lRoaEAiUJnPzh9UIHK9u3nVYt6ezGdDqJGDtsu46/0mMJ2/vTsoLwgHV+meBV14bhV3o+kNlez9tQ4d8iWLl9ATUq0cgwV6CtNilZ3O8LknaN9uK2EAAViDnicljMzJsaqRXWhCtN8wCTw2IMWaHsclFNbBcWzlAiKusEsoFT1RAKYlREyI6drwIDAQAB";

    File rootPath=null;
    File subfolderPath=null;
    File file=null;
    File newRootPath=null;
    File newSubfolderPath=null;
    File newFile=null;

    long currentTime = 0;

    IabHelper mHelper;
    static final String PREMIUM = "premium";
    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;

    int trialEnded = -1;

    boolean mIsPremium = false;

    Activity activity;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activity = this;
        currentTime = System.currentTimeMillis();
        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d("A", "Creating IAB helper.");
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);
        // will be called once setup completes.
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    return;
                }
                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;
                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d("A", "Setup successful. Querying inventory.");
                mHelper.queryInventoryAsync(mGotInventoryListener);
            }
        });

        checkingApp();

        if(isFileExists(newFile)){
            super.onCreate(savedInstanceState);
            setContentView(R.layout.trial_end_layout);
            try {
                getActionBar().hide();
            }catch (NullPointerException e){}
            TextView button = (TextView) findViewById(R.id.buy_full_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onUpgradeAppButtonClicked();
                }
            });
        }else {


        ApplicationLoader.postInitApplication();

        if (!UserConfig.isClientActivated()) {
            Intent intent = getIntent();
            if (intent != null && intent.getAction() != null && (Intent.ACTION_SEND.equals(intent.getAction()) || intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))) {
                super.onCreate(savedInstanceState);
                finish();
                return;
            }
            if (intent != null && !intent.getBooleanExtra("fromIntro", false)) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", MODE_PRIVATE);
                Map<String, ?> state = preferences.getAll();
                if (state.isEmpty()) {
                    Intent intent2 = new Intent(this, IntroActivity.class);
                    startActivity(intent2);
                    super.onCreate(savedInstanceState);
                    finish();
                    return;
                }
            }
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);

        super.onCreate(savedInstanceState);

        actionBarLayout = new ActionBarLayout(this);


            if (AndroidUtilities.isTablet()) {
                setContentView(R.layout.launch_layout_tablet);
                shadowTablet = (FrameLayout) findViewById(R.id.shadow_tablet);
                buttonLayoutTablet = (LinearLayout) findViewById(R.id.launch_button_layout);
                shadowTabletSide = (FrameLayout) findViewById(R.id.shadow_tablet_side);
                backgroundTablet = (ImageView) findViewById(R.id.launch_background);

                shadowTablet.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (!actionBarLayout.fragmentsStack.isEmpty() && event.getAction() == MotionEvent.ACTION_UP) {
                            float x = event.getX();
                            float y = event.getY();
                            int location[] = new int[2];
                            layersActionBarLayout.getLocationOnScreen(location);
                            int viewX = location[0];
                            int viewY = location[1];

                            if (x > viewX && x < viewX + layersActionBarLayout.getWidth() && y > viewY && y < viewY + layersActionBarLayout.getHeight()) {
                                return false;
                            } else {
                                if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                                    for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                                        layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                                        a--;
                                    }
                                    layersActionBarLayout.closeLastFragment(true);
                                }
                                return true;
                            }
                        }
                        return false;
                    }
                });

                shadowTablet.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    }
                });

                RelativeLayout launchLayout = (RelativeLayout) findViewById(R.id.launch_layout);

                layersActionBarLayout = new ActionBarLayout(this);
                layersActionBarLayout.setBackgroundView(shadowTablet);
                layersActionBarLayout.setUseAlphaAnimations(true);
                layersActionBarLayout.setBackgroundResource(R.drawable.boxshadow);
                launchLayout.addView(layersActionBarLayout);
                RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) layersActionBarLayout.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.dp(498);
                relativeLayoutParams.height = AndroidUtilities.dp(528);
                relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                layersActionBarLayout.setLayoutParams(relativeLayoutParams);
                layersActionBarLayout.init(layerFragmentsStack);
                layersActionBarLayout.setDelegate(this);
                layersActionBarLayout.setVisibility(View.GONE);

                launchLayout.addView(actionBarLayout, 2);
                relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.dp(320);
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                rightActionBarLayout = new ActionBarLayout(this);
                launchLayout.addView(rightActionBarLayout, 3);
                relativeLayoutParams = (RelativeLayout.LayoutParams) rightActionBarLayout.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.dp(320);
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                rightActionBarLayout.setLayoutParams(relativeLayoutParams);
                rightActionBarLayout.init(rightFragmentsStack);
                rightActionBarLayout.setDelegate(this);

                TextView button = (TextView) findViewById(R.id.new_group_button);
                button.setText(LocaleController.getString("NewGroup", R.string.NewGroup));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        presentFragment(new GroupCreateActivity());
                    }
                });


                //TODO: hide tablet buttons
               /* button = (TextView) findViewById(R.id.new_secret_button);
                button.setText(LocaleController.getString("NewSecretChat", R.string.NewSecretChat));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        args.putBoolean("createSecretChat", true);
                        presentFragment(new ContactsActivity(args));
                    }
                });*/
/*
                button = (TextView) findViewById(R.id.new_broadcast_button);
                button.setText(LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bundle args = new Bundle();
                        args.putBoolean("broadcast", true);
                        presentFragment(new GroupCreateActivity(args));
                    }
                });*/


                button = (TextView) findViewById(R.id.contacts_button);
                button.setText(LocaleController.getString("Contacts", R.string.Contacts));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        presentFragment(new ContactsActivity(null));
                    }
                });

                button = (TextView) findViewById(R.id.settings_button);
                button.setText(LocaleController.getString("Settings", R.string.Settings));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        presentFragment(new SettingsActivity());
                    }
                });

                button = (TextView) findViewById(R.id.goto_site_button);
                button.setText(getString(R.string.HowItWorks));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse("http://www.easychad.com"));
                        startActivity(i);
                        //presentFragment(new SettingsActivity());
                    }
                });
                button = (TextView) findViewById(R.id.goto_login_button);
                button.setText(getString(R.string.SiteRegistration));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse("http://www.easychad.com"));
                        startActivity(i);
                        // presentFragment(new SettingsActivity());
                    }
                });
                button = (TextView) findViewById(R.id.pay_button);
                button.setText(getString(R.string.Pay));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mHelper.launchPurchaseFlow(activity,PREMIUM, RC_REQUEST,
                                mPurchaseFinishedListener,"");
                        //presentFragment(new SettingsActivity());
                        //presentFragment(new SettingsActivity());
                    }
                });

            } else {
                setContentView(actionBarLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }


            actionBarLayout.init(mainFragmentsStack);
            actionBarLayout.setDelegate(this);

            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                AndroidUtilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
            }

            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);
            currentConnectionState = ConnectionsManager.getInstance().getConnectionState();

            NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeOtherAppActivities);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didUpdatedConnectionState);

            if (actionBarLayout.fragmentsStack.isEmpty()) {
                if (!UserConfig.isClientActivated()) {
                    actionBarLayout.addFragmentToStack(new LoginActivity());
                } else {
                    actionBarLayout.addFragmentToStack(new MessagesActivity(null));
                }

                try {
                    if (savedInstanceState != null) {
                        String fragmentName = savedInstanceState.getString("fragment");
                        if (fragmentName != null) {
                            Bundle args = savedInstanceState.getBundle("args");
                            if (fragmentName.equals("chat")) {
                                if (args != null) {
                                    ChatActivity chat = new ChatActivity(args);
                                    if (actionBarLayout.addFragmentToStack(chat)) {
                                        chat.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                            } else if (fragmentName.equals("settings")) {
                                SettingsActivity settings = new SettingsActivity();
                                actionBarLayout.addFragmentToStack(settings);
                                settings.restoreSelfArgs(savedInstanceState);
                            } else if (fragmentName.equals("group")) {
                                if (args != null) {
                                    GroupCreateFinalActivity group = new GroupCreateFinalActivity(args);
                                    if (actionBarLayout.addFragmentToStack(group)) {
                                        group.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                            } else if (fragmentName.equals("chat_profile")) {
                                if (args != null) {
                                    ChatProfileActivity profile = new ChatProfileActivity(args);
                                    if (actionBarLayout.addFragmentToStack(profile)) {
                                        profile.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                            } else if (fragmentName.equals("wallpapers")) {
                                SettingsWallpapersActivity settings = new SettingsWallpapersActivity();
                                actionBarLayout.addFragmentToStack(settings);
                                settings.restoreSelfArgs(savedInstanceState);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }

            handleIntent(getIntent(), false, savedInstanceState != null);
            needLayout();
        }
    }

    private void handleIntent(Intent intent, boolean isNew, boolean restore) {
        boolean pushOpened = false;

        Integer push_user_id = 0;
        Integer push_chat_id = 0;
        Integer push_enc_id = 0;
        Integer open_settings = 0;
        boolean showDialogsList = false;

        photoPathsArray = null;
        videoPath = null;
        sendingText = null;
        documentsPathsArray = null;
        documentsOriginalPathsArray = null;
        contactsToSend = null;

        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            if (intent != null && intent.getAction() != null && !restore) {
                if (Intent.ACTION_SEND.equals(intent.getAction())) {
                    boolean error = false;
                    String type = intent.getType();
                    if (type != null && type.equals("text/plain")) {
                        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

                        if (text != null && text.length() != 0) {
                            if ((text.startsWith("http://") || text.startsWith("https://")) && subject != null && subject.length() != 0) {
                                text = subject + "\n" + text;
                            }
                            sendingText = text;
                        } else {
                            error = true;
                        }
                    } else if (type != null && type.equals(ContactsContract.Contacts.CONTENT_VCARD_TYPE)) {
                        try {
                            Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                            if (uri != null) {
                                ContentResolver cr = getContentResolver();
                                InputStream stream = cr.openInputStream(uri);

                                String name = null;
                                String nameEncoding = null;
                                String nameCharset = null;
                                ArrayList<String> phones = new ArrayList<String>();
                                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                                String line = null;
                                while ((line = bufferedReader.readLine()) != null) {
                                    String[] args = line.split(":");
                                    if (args.length != 2) {
                                        continue;
                                    }
                                    if (args[0].startsWith("FN")) {
                                        String[] params = args[0].split(";");
                                        for (String param : params) {
                                            String[] args2 = param.split("=");
                                            if (args2.length != 2) {
                                                continue;
                                            }
                                            if (args2[0].equals("CHARSET")) {
                                                nameCharset = args2[1];
                                            } else if (args2[0].equals("ENCODING")) {
                                                nameEncoding = args2[1];
                                            }
                                        }
                                        name = args[1];
                                        if (nameEncoding != null && nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                                            while (name.endsWith("=") && nameEncoding != null) {
                                                name = name.substring(0, name.length() - 1);
                                                line = bufferedReader.readLine();
                                                if (line == null) {
                                                    break;
                                                }
                                                name += line;
                                            }
                                            byte[] bytes = Utilities.decodeQuotedPrintable(name.getBytes());
                                            if (bytes != null && bytes.length != 0) {
                                                String decodedName = new String(bytes, nameCharset);
                                                if (decodedName != null) {
                                                    name = decodedName;
                                                }
                                            }
                                        }
                                    } else if (args[0].startsWith("TEL")) {
                                        String phone = PhoneFormat.stripExceptNumbers(args[1], true);
                                        if (phone.length() > 0) {
                                            phones.add(phone);
                                        }
                                    }
                                }
                                if (name != null && !phones.isEmpty()) {
                                    contactsToSend = new ArrayList<TLRPC.User>();
                                    for (String phone : phones) {
                                        TLRPC.User user = new TLRPC.TL_userContact();
                                        user.phone = phone;
                                        user.first_name = name;
                                        user.last_name = "";
                                        user.id = 0;
                                        contactsToSend.add(user);
                                    }
                                }
                            } else {
                                error = true;
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                            error = true;
                        }
                    } else {
                        Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        if (parcelable == null) {
                            return;
                        }
                        String path = null;
                        if (!(parcelable instanceof Uri)) {
                            parcelable = Uri.parse(parcelable.toString());
                        }
                        Uri uri = (Uri) parcelable;
                        if (uri != null && type != null && type.startsWith("image/")) {
                            String tempPath = Utilities.getPath(uri);
                            if (photoPathsArray == null) {
                                photoPathsArray = new ArrayList<Uri>();
                            }
                            photoPathsArray.add(uri);
                        } else {
                            path = Utilities.getPath(uri);
                            if (path != null) {
                                if (path.startsWith("file:")) {
                                    path = path.replace("file://", "");
                                }
                                if (type != null && type.startsWith("video/")) {
                                    videoPath = path;
                                } else {
                                    if (documentsPathsArray == null) {
                                        documentsPathsArray = new ArrayList<String>();
                                        documentsOriginalPathsArray = new ArrayList<String>();
                                    }
                                    documentsPathsArray.add(path);
                                    documentsOriginalPathsArray.add(uri.toString());
                                }
                            } else {
                                error = true;
                            }
                        }
                        if (error) {
                            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if (intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
                    boolean error = false;
                    try {
                        ArrayList<Parcelable> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                        String type = intent.getType();
                        if (uris != null) {
                            if (type != null && type.startsWith("image/")) {
                                for (Parcelable parcelable : uris) {
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    Uri uri = (Uri) parcelable;
                                    if (photoPathsArray == null) {
                                        photoPathsArray = new ArrayList<Uri>();
                                    }
                                    photoPathsArray.add(uri);
                                }
                            } else {
                                for (Parcelable parcelable : uris) {
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    String path = Utilities.getPath((Uri) parcelable);
                                    String originalPath = parcelable.toString();
                                    if (originalPath == null) {
                                        originalPath = path;
                                    }
                                    if (path != null) {
                                        if (path.startsWith("file:")) {
                                            path = path.replace("file://", "");
                                        }
                                        if (documentsPathsArray == null) {
                                            documentsPathsArray = new ArrayList<String>();
                                            documentsOriginalPathsArray = new ArrayList<String>();
                                        }
                                        documentsPathsArray.add(path);
                                        documentsOriginalPathsArray.add(originalPath);
                                    }
                                }
                            }
                        } else {
                            error = true;
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        error = true;
                    }
                    if (error) {
                        Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
                    }
                } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                    try {
                        Cursor cursor = getContentResolver().query(intent.getData(), null, null, null, null);
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                int userId = cursor.getInt(cursor.getColumnIndex("DATA4"));
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                push_user_id = userId;
                            }
                            cursor.close();
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (intent.getAction().equals("ml.easychad.lax.messenger.OPEN_ACCOUNT")) {
                    open_settings = 1;
                }
            }

            if (intent.getAction() != null && intent.getAction().startsWith("com.tmessages.openchat") && !restore) {
                int chatId = intent.getIntExtra("chatId", 0);
                int userId = intent.getIntExtra("userId", 0);
                int encId = intent.getIntExtra("encId", 0);
                if (chatId != 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                    push_chat_id = chatId;
                } else if (userId != 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                    push_user_id = userId;
                } else if (encId != 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                    push_enc_id = encId;
                } else {
                    showDialogsList = true;
                }
            }
        }

        if (push_user_id != 0) {
            if (push_user_id == UserConfig.getClientUserId()) {
                open_settings = 1;
            } else {
                Bundle args = new Bundle();
                args.putInt("user_id", push_user_id);
                ChatActivity fragment = new ChatActivity(args);
                if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                    pushOpened = true;
                }
            }
        } else if (push_chat_id != 0) {
            Bundle args = new Bundle();
            args.putInt("chat_id", push_chat_id);
            ChatActivity fragment = new ChatActivity(args);
            if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                pushOpened = true;
            }
        } else if (push_enc_id != 0) {
            Bundle args = new Bundle();
            args.putInt("enc_id", push_enc_id);
            ChatActivity fragment = new ChatActivity(args);
            if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                pushOpened = true;
            }
        } else if (showDialogsList) {
            if (!AndroidUtilities.isTablet()) {
                actionBarLayout.removeAllFragments();
            }
            pushOpened = false;
            isNew = false;
        }
        if (videoPath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null) {
            if (!AndroidUtilities.isTablet()) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            }
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putString("selectAlertString", LocaleController.getString("SendMessagesTo", R.string.SendMessagesTo));
            args.putString("selectAlertStringGroup", LocaleController.getString("SendMessagesToGroup", R.string.SendMessagesToGroup));
            MessagesActivity fragment = new MessagesActivity(args);
            fragment.setDelegate(this);
            actionBarLayout.presentFragment(fragment, false, true, true);
            pushOpened = true;
            if (PhotoViewer.getInstance().isVisible()) {
                PhotoViewer.getInstance().closePhoto(false);
            }

            if (AndroidUtilities.isTablet()) {
                actionBarLayout.showLastFragment();
                rightActionBarLayout.showLastFragment();
            }
        }
        if (open_settings != 0) {
            actionBarLayout.presentFragment(new SettingsActivity(), false, true, true);
            pushOpened = true;
        }
        if (!pushOpened && !isNew) {
            if (AndroidUtilities.isTablet()) {
                if (UserConfig.isClientActivated()) {
                    if (actionBarLayout.fragmentsStack.isEmpty()) {
                        actionBarLayout.addFragmentToStack(new MessagesActivity(null));
                    }
                } else {
                    if (layersActionBarLayout.fragmentsStack.isEmpty()) {
                        layersActionBarLayout.addFragmentToStack(new LoginActivity());
                    }
                }
            } else {
                if (actionBarLayout.fragmentsStack.isEmpty()) {
                    if (!UserConfig.isClientActivated()) {
                        actionBarLayout.addFragmentToStack(new LoginActivity());
                    } else {
                        actionBarLayout.addFragmentToStack(new MessagesActivity(null));
                    }
                }
            }
            actionBarLayout.showLastFragment();
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.showLastFragment();
                rightActionBarLayout.showLastFragment();
            }
        }

        intent.setAction(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false);
    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id, boolean param) {
        if (dialog_id != 0) {
            int lower_part = (int)dialog_id;
            int high_id = (int)(dialog_id >> 32);

            Bundle args = new Bundle();
            args.putBoolean("scrollToTopOnResume", true);
            if (!AndroidUtilities.isTablet()) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            }
            if (lower_part != 0) {
                if (high_id == 1) {
                    args.putInt("chat_id", lower_part);
                } else {
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args.putInt("chat_id", -lower_part);
                    }
                }
            } else {
                args.putInt("enc_id", high_id);
            }
            ChatActivity fragment = new ChatActivity(args);

            if (videoPath != null) {
                if(android.os.Build.VERSION.SDK_INT >= 16) {
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.presentFragment(fragment, false, true, true);
                    }

                    if (!AndroidUtilities.isTablet()) {
                        actionBarLayout.addFragmentToStack(fragment, actionBarLayout.fragmentsStack.size() - 1);
                    }

                    if (!fragment.openVideoEditor(videoPath, true)) {
                        if (!AndroidUtilities.isTablet()) {
                            messageFragment.finishFragment(true);
                        }
                    }
                } else {
                    actionBarLayout.presentFragment(fragment, true);
                    SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id);
                }
            } else {
                actionBarLayout.presentFragment(fragment, true);
                if (sendingText != null) {
                    fragment.processSendingText(sendingText);
                }
                if (photoPathsArray != null) {
                    SendMessagesHelper.prepareSendingPhotos(null, photoPathsArray, dialog_id);
                }
                if (documentsPathsArray != null) {
                    SendMessagesHelper.prepareSendingDocuments(documentsPathsArray, documentsOriginalPathsArray, dialog_id);
                }
                if (contactsToSend != null && !contactsToSend.isEmpty()) {
                    for (TLRPC.User user : contactsToSend) {
                        SendMessagesHelper.getInstance().sendMessage(user, dialog_id);
                    }
                }
            }

            photoPathsArray = null;
            videoPath = null;
            sendingText = null;
            documentsPathsArray = null;
            documentsOriginalPathsArray = null;
            contactsToSend = null;
        }
    }

    private void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        if(!isFileExists(newFile)) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeOtherAppActivities);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didUpdatedConnectionState);
        }
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
    }

    public void needLayout() {
        if (AndroidUtilities.isTablet()) {
            if (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                tabletFullSize = false;
                int leftWidth = AndroidUtilities.displaySize.x / 100 * 35;
                if (leftWidth < AndroidUtilities.dp(320)) {
                    leftWidth = AndroidUtilities.dp(320);
                }

                RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = leftWidth;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) shadowTabletSide.getLayoutParams();
                relativeLayoutParams.leftMargin = leftWidth;
                shadowTabletSide.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) rightActionBarLayout.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.displaySize.x - leftWidth;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                relativeLayoutParams.leftMargin = leftWidth;
                rightActionBarLayout.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) buttonLayoutTablet.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.displaySize.x - leftWidth;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                relativeLayoutParams.leftMargin = leftWidth;
                buttonLayoutTablet.setLayoutParams(relativeLayoutParams);

                if (AndroidUtilities.isSmallTablet() && actionBarLayout.fragmentsStack.size() == 2) {
                    BaseFragment chatFragment = actionBarLayout.fragmentsStack.get(1);
                    chatFragment.onPause();
                    actionBarLayout.fragmentsStack.remove(1);
                    actionBarLayout.showLastFragment();
                    rightActionBarLayout.fragmentsStack.add(chatFragment);
                    rightActionBarLayout.showLastFragment();
                }

                rightActionBarLayout.setVisibility(rightActionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
                buttonLayoutTablet.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() && rightActionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
                backgroundTablet.setVisibility(rightActionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
                shadowTabletSide.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                tabletFullSize = true;

                RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                shadowTabletSide.setVisibility(View.GONE);
                rightActionBarLayout.setVisibility(View.GONE);
                backgroundTablet.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
                buttonLayoutTablet.setVisibility(View.GONE);

                if (rightActionBarLayout.fragmentsStack.size() == 1) {
                    BaseFragment chatFragment = rightActionBarLayout.fragmentsStack.get(0);
                    chatFragment.onPause();
                    rightActionBarLayout.fragmentsStack.remove(0);
                    actionBarLayout.presentFragment(chatFragment, false, true, false);
                }
            }
        }
    }

    public void fixLayout() {
        if (AndroidUtilities.isTablet()) {
            if (actionBarLayout == null) {
                return;
            }
            actionBarLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    needLayout();
                    if (actionBarLayout != null) {
                        if (Build.VERSION.SDK_INT < 16) {
                            actionBarLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            actionBarLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(actionBarLayout!=null) {
            if (actionBarLayout.fragmentsStack.size() != 0) {
                BaseFragment fragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                fragment.onActivityResultFragment(requestCode, resultCode, data);
            }
            if (AndroidUtilities.isTablet()) {
                if (rightActionBarLayout.fragmentsStack.size() != 0) {
                    BaseFragment fragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                    fragment.onActivityResultFragment(requestCode, resultCode, data);
                }
                if (layersActionBarLayout.fragmentsStack.size() != 0) {
                    BaseFragment fragment = layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1);
                    fragment.onActivityResultFragment(requestCode, resultCode, data);
                }
            }
        }

        if(mHelper!=null){
            // Pass on the activity result to the helper for handling
            if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
                // not handled, so handle it ourselves (here's where you'd
                // perform any handling of activity results not related to in-app
                // billing...
                super.onActivityResult(requestCode, resultCode, data);
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(!isFileExists(newFile)) {
            if(actionBarLayout!=null) {
                actionBarLayout.onPause();
                if (AndroidUtilities.isTablet()) {
                    rightActionBarLayout.onPause();
                    layersActionBarLayout.onPause();
                }
                ApplicationLoader.mainInterfacePaused = true;
                ConnectionsManager.getInstance().setAppPaused(true, false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if(!isFileExists(newFile)) {
            if(actionBarLayout!=null) {
                PhotoViewer.getInstance().destroyPhotoViewer();
                SecretPhotoViewer.getInstance().destroyPhotoViewer();
            }
        }
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }
        super.onDestroy();
        onFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!isFileExists(newFile)) {
            if(actionBarLayout!=null) {
                actionBarLayout.onResume();
                if (AndroidUtilities.isTablet()) {
                    rightActionBarLayout.onResume();
                    layersActionBarLayout.onResume();
                }
                Utilities.checkForCrashes(this);
                Utilities.checkForUpdates(this);
                ApplicationLoader.mainInterfacePaused = false;
                ConnectionsManager.getInstance().setAppPaused(false, false);
                actionBarLayout.getActionBar().setBackOverlayVisible(currentConnectionState != 0);
            }
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        AndroidUtilities.checkDisplaySize();
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            for (BaseFragment fragment : actionBarLayout.fragmentsStack) {
                fragment.onFragmentDestroy();
            }
            actionBarLayout.fragmentsStack.clear();
            if (AndroidUtilities.isTablet()) {
                for (BaseFragment fragment : layersActionBarLayout.fragmentsStack) {
                    fragment.onFragmentDestroy();
                }
                layersActionBarLayout.fragmentsStack.clear();
                for (BaseFragment fragment : rightActionBarLayout.fragmentsStack) {
                    fragment.onFragmentDestroy();
                }
                rightActionBarLayout.fragmentsStack.clear();
            }
            Intent intent2 = new Intent(this, IntroActivity.class);
            startActivity(intent2);
            onFinish();
            finish();
        } else if (id == NotificationCenter.closeOtherAppActivities) {
            if (args[0] != this) {
                onFinish();
            }
        } else if (id == NotificationCenter.didUpdatedConnectionState) {
            int state = (Integer)args[0];
            if (currentConnectionState != state) {
                FileLog.e("tmessages", "switch to state " + state);
                currentConnectionState = state;
                actionBarLayout.getActionBar().setBackOverlayVisible(currentConnectionState != 0);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            BaseFragment lastFragment = null;
            if (AndroidUtilities.isTablet()) {
                if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1);
                } else if (!rightActionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                } else if (!actionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                }
            } else {
                if (!actionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                }
            }

            if (lastFragment != null) {
                Bundle args = lastFragment.getArguments();
                if (lastFragment instanceof ChatActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat");
                } else if (lastFragment instanceof SettingsActivity) {
                    outState.putString("fragment", "settings");
                } else if (lastFragment instanceof GroupCreateFinalActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "group");
                } else if (lastFragment instanceof SettingsWallpapersActivity) {
                    outState.putString("fragment", "wallpapers");
                } else if (lastFragment instanceof ChatProfileActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat_profile");
                }
                lastFragment.saveSelfArgs(outState);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true);
        } else {
            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout.getVisibility() == View.VISIBLE) {
                    layersActionBarLayout.onBackPressed();
                } else {
                    boolean cancel = false;
                    if (rightActionBarLayout.getVisibility() == View.VISIBLE && !rightActionBarLayout.fragmentsStack.isEmpty()) {
                        BaseFragment lastFragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                        cancel = !lastFragment.onBackPressed();
                    }
                    if (!cancel) {
                        actionBarLayout.onBackPressed();
                    }
                }
            } else {
                actionBarLayout.onBackPressed();
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        actionBarLayout.onLowMemory();
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onLowMemory();
            layersActionBarLayout.onLowMemory();
        }
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        actionBarLayout.onActionModeStarted(mode);
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onActionModeStarted(mode);
            layersActionBarLayout.onActionModeStarted(mode);
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        actionBarLayout.onActionModeFinished(mode);
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onActionModeFinished(mode);
            layersActionBarLayout.onActionModeFinished(mode);
        }
    }

    @Override
    public boolean onPreIme() {
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true);
            return true;
        }
        return false;
    }

    @Override
    public void onOverlayShow(View view, BaseFragment fragment) {
        if (view == null || fragment == null || actionBarLayout.fragmentsStack.isEmpty()) {
            return;
        }
        View backStatusButton = view.findViewById(R.id.back_button);
        TextView statusText = (TextView)view.findViewById(R.id.status_text);
        backStatusButton.setVisibility(actionBarLayout.fragmentsStack.get(0) == fragment ? View.GONE : View.VISIBLE);
        view.setEnabled(actionBarLayout.fragmentsStack.get(0) != fragment);
        if (currentConnectionState == 1) {
            statusText.setText(LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork));
        } else if (currentConnectionState == 2) {
            statusText.setText(LocaleController.getString("Connecting", R.string.Connecting));
        } else if (currentConnectionState == 3) {
            statusText.setText(LocaleController.getString("Updating", R.string.Updating));
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(!isFileExists(newFile)) {
            if(actionBarLayout!=null) {
                if (AndroidUtilities.isTablet()) {
                    if (layersActionBarLayout.getVisibility() == View.VISIBLE && !layersActionBarLayout.fragmentsStack.isEmpty()) {
                        layersActionBarLayout.onKeyUp(keyCode, event);
                    } else if (rightActionBarLayout.getVisibility() == View.VISIBLE && !rightActionBarLayout.fragmentsStack.isEmpty()) {
                        rightActionBarLayout.onKeyUp(keyCode, event);
                    } else {
                        actionBarLayout.onKeyUp(keyCode, event);
                    }
                } else {
                    actionBarLayout.onKeyUp(keyCode, event);
                }
                return super.onKeyUp(keyCode, event);
            }else {
                finish();
                return false;
            }
        }else{
            finish();
            return false;
        }
    }

    @Override
    public boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (fragment instanceof MessagesActivity) {
                MessagesActivity messagesActivity = (MessagesActivity)fragment;
                if (messagesActivity.getDelegate() == null && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.setVisibility(View.GONE);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                            buttonLayoutTablet.setVisibility(View.VISIBLE);
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            }
            if (fragment instanceof ChatActivity) {
                if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    buttonLayoutTablet.setVisibility(View.GONE);
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.presentFragment(fragment, removeLast, true, false);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else {
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    if (actionBarLayout.fragmentsStack.size() > 1) {
                        actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false);
                        return false;
                    }
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.setVisibility(View.VISIBLE);
                if (fragment instanceof LoginActivity) {
                    buttonLayoutTablet.setVisibility(View.GONE);
                    backgroundTablet.setVisibility(View.VISIBLE);
                    shadowTabletSide.setVisibility(View.GONE);
                    shadowTablet.setBackgroundColor(0x00000000);
                } else {
                    shadowTablet.setBackgroundColor(0x7F000000);
                }
                layersActionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false);
                return false;
            }
            return true;
        } else {
            return true;
        }
    }

    @Override
    public boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (fragment instanceof MessagesActivity) {
                MessagesActivity messagesActivity = (MessagesActivity)fragment;
                if (messagesActivity.getDelegate() == null && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.addFragmentToStack(fragment);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.setVisibility(View.GONE);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                            buttonLayoutTablet.setVisibility(View.VISIBLE);
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            } else if (fragment instanceof ChatActivity) {
                if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    buttonLayoutTablet.setVisibility(View.GONE);
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.addFragmentToStack(fragment);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(true);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    actionBarLayout.addFragmentToStack(fragment);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(true);
                    }
                    return false;
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.setVisibility(View.VISIBLE);
                if (fragment instanceof LoginActivity) {
                    buttonLayoutTablet.setVisibility(View.GONE);
                    backgroundTablet.setVisibility(View.VISIBLE);
                    shadowTabletSide.setVisibility(View.GONE);
                    shadowTablet.setBackgroundColor(0x00000000);
                } else {
                    shadowTablet.setBackgroundColor(0x7F000000);
                }
                layersActionBarLayout.addFragmentToStack(fragment);
                return false;
            }
            return true;
        } else {
            return true;
        }
    }

    @Override
    public boolean needCloseLastFragment(ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (layout == actionBarLayout && layout.fragmentsStack.size() <= 1) {
                onFinish();
                finish();
                return false;
            } else if (layout == rightActionBarLayout) {
                if (!tabletFullSize) {
                    buttonLayoutTablet.setVisibility(View.VISIBLE);
                    backgroundTablet.setVisibility(View.VISIBLE);
                }
            } else if (layout == layersActionBarLayout && actionBarLayout.fragmentsStack.isEmpty() && layersActionBarLayout.fragmentsStack.size() == 1) {
                onFinish();
                finish();
                return false;
            }
        } else {
            if (layout.fragmentsStack.size() <= 1) {
                onFinish();
                finish();
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRebuildAllFragments(ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (layout == layersActionBarLayout) {
                rightActionBarLayout.rebuildAllFragmentViews(true);
                rightActionBarLayout.showLastFragment();
                actionBarLayout.rebuildAllFragmentViews(true);
                actionBarLayout.showLastFragment();

                TextView button = (TextView)findViewById(R.id.new_group_button);
                button.setText(LocaleController.getString("NewGroup", R.string.NewGroup));
                //TODO
                button = (TextView)findViewById(R.id.new_secret_button);
                button.setText(LocaleController.getString("NewSecretChat", R.string.NewSecretChat));
//                button = (TextView)findViewById(R.id.new_broadcast_button);
//                button.setText(LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
                button = (TextView)findViewById(R.id.contacts_button);
                button.setText(LocaleController.getString("Contacts", R.string.Contacts));
                button = (TextView)findViewById(R.id.settings_button);
                button.setText(LocaleController.getString("Settings", R.string.Settings));
                button = (TextView)findViewById(R.id.goto_site_button);
                button.setText(getString(R.string.HowItWorks));
                button = (TextView)findViewById(R.id.goto_login_button);
                button.setText(getString(R.string.SiteRegistration));
                button = (TextView)findViewById(R.id.pay_button);
                button.setText(getString(R.string.Pay));
            }
        }
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;
            // Is it a failure?
            if (result.isFailure()) {
                return;
            }

            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(PREMIUM);
            mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
            Log.d("LOG", "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

            if(trialEnded==1){
                if(mIsPremium){
                    TrialController.INSTANCE.setAppEnable(true);
                    Log.d("LOG","You are premium");
                }else{
                    TrialController.INSTANCE.setAppEnable(false);
                    Log.d("LOG","Make purchase, please");
                    newRootPath.mkdirs();
                    newSubfolderPath.mkdirs();
                    try {
                        newFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                    //TODO --> Вікно оплати
                }
            }
            Log.d("A", "Initial inventory query finished; enabling main UI.");
        }
    };

    /** Verifies the developer payload of a purchase. */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d("LOG", "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                Log.d("LOG","Error purchasing: " + result);

                return;
            }
            if (!verifyDeveloperPayload(purchase)) {

                return;
            }

            Log.d("A", "Purchase successful.");

            if (purchase.getSku().equals(PREMIUM)) {
                // bought the premium upgrade!
                Log.d("A", "Purchase is premium upgrade. Congratulating user.");
                mIsPremium = true;
                newFile.delete();
                Intent intent = getIntent();
                finish();
                startActivity(intent);

                //TODO: END
            }

        }
    };






    public void createSignatureFile(){
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                rootPath.mkdirs();
                if (rootPath.isDirectory()) {
                    try {
                        subfolderPath.mkdir();
                        if (subfolderPath.isDirectory()) {
                            file.createNewFile();
                            writeToFile(file,currentTime+"");
                            Log.d("LOG","New file created");
                        }
                    } catch (Exception e) {

                    }
                }
            } else {
                //TODO: Media not mounted
            }
            //checkSaveToGalleryFiles();
        } catch (Exception e) {

        }
    }
    /*public void checkSaveToGalleryFiles() {
        try {
            File telegramPath = new File(Environment.getExternalStorageDirectory(), "sys_info");
            File imagePath = new File(telegramPath,  "internal_system_keys");
            imagePath.mkdir();


            if (false) {
                if (imagePath.isDirectory()) {
                    new File(imagePath, ".nomedia").delete();
                }

            } else {
                if (imagePath.isDirectory()) {
                    new File(imagePath, ".nomedia").createNewFile();
                }
            }
        } catch (Exception e) {
            //FileLog.e("tmessages", e);
        }
    }*/

    public String readFromFiles(File file){
        String information = "";
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line;
            while((line = in.readLine()) != null)
                information = line;
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return information;
    }

    public  File getDir() {
        if (Environment.getExternalStorageState() == null || Environment.getExternalStorageState().startsWith(Environment.MEDIA_MOUNTED)) {
            try {
                File file = getExternalCacheDir();
                if (file != null) {
                    return file;
                }
            } catch (Exception e) {
            }
        }
        try {
            File file = getCacheDir();
            if (file != null) {
                return file;
            }
        } catch (Exception e) {
        }
        return new File("");
    }

    public boolean isFileExists(File file){
        if(file.exists()){
            return true;
        }
        return false;
    }

    public void writeToFile(File file,String text){
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(file, "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        assert writer != null;
        writer.println(text);
        writer.close();
    }

    public void checkingApp(){
        File cachePath = getDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {

            }
        }



        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.d("LOG","You are there");
            rootPath = new File(Environment.getExternalStorageDirectory(), "opt");
            subfolderPath = new File(rootPath, "internal_system_keys");
            file = new File(subfolderPath, "keys.txt");
            newRootPath = new File(Environment.getExternalStorageDirectory(), "src");
            newSubfolderPath = new File(newRootPath, "ti/di/bi/l");
            newFile = new File(newSubfolderPath, "keys.txt");
        }

        if(isFileExists(newFile)){
            TrialController.INSTANCE.setAppEnable(false);
        }else{
            TrialController.INSTANCE.setAppEnable(true);
        }

        if(TrialController.INSTANCE.isAppEnable()) {
            if (file != null) {
                if (isFileExists(file)) {
                    Log.d("LOG", "File already exists");
                    //TODO: read data from file and check time
                    final String sTime = readFromFiles(file);
                    long dada  = 60000*60;
                    long difference = dada*24*60;//86400000*2;//Trial period


                    long endTrialTime = Long.parseLong(sTime) + difference;
                    if (currentTime > endTrialTime) {
                        trialEnded = 1;
                        Log.d("LOG", "End trial period");

                    } else {
                        trialEnded = 0;
                        Log.d("LOG", "Trial period - welcome!");
                    }

                } else {
                    Log.d("LOG", "File doesn't exists");
                    createSignatureFile();
                }
            } else {
                Log.d("LOG", "Problem with path");
            }
        }else{

        }

    }

    // User clicked the "Upgrade to Premium" button.
    public void onUpgradeAppButtonClicked() {
        Log.d("A", "Upgrade button clicked; launching purchase flow for upgrade.");
        String payload = "";
        mHelper.launchPurchaseFlow(this, PREMIUM, RC_REQUEST,
                mPurchaseFinishedListener, payload);
    }





}
