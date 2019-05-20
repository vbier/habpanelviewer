package de.vier_bier.habpanelviewer;

public final class Constants {
    /** activity request related constants **/
    public static final int REQUEST_DEVICE_ADMIN = 101;
    public static final int REQUEST_PICK_APPLICATION = 102;
    public static final int REQUEST_MEDIA_PROJECTION = 103;

    /** permission related constants **/
    public static final int REQUEST_CAMERA = 201;
    public static final int REQUEST_WRITE_EXTERNAL_STORAGE = 202;
    public static final int REQUEST_READ_EXTERNAL_STORAGE = 203;
    public static final int REQUEST_WEBRTC = 204;

    /** Intent related constants **/
    public static final String INTENT_FLAG_CRASH = "crash";
    public static final String INTENT_FLAG_RESTART_COUNT = "restartCount";

    public static final String INTENT_FLAG_CAMERA_ENABLED = "camera_enabled";
    public static final String INTENT_FLAG_FLASH_ENABLED = "flash_enabled";
    public static final String INTENT_FLAG_MOTION_ENABLED = "motion_enabled";

    public static final String INTENT_ACTION_KEEP_SCREEN_ON = "ACTION_KEEP_SCREEN_ON";
    public static final String INTENT_FLAG_KEEP_SCREEN_ON = "keepScreenOn";
    public static final String INTENT_FLAG_DIM = "dim";

    public static final String INTENT_ACTION_SET_BRIGHTNESS = "ACTION_SET_BRIGHTNESS";
    public static final String INTENT_FLAG_BRIGHTNESS = "brightness";

    public static final String INTENT_ACTION_SET_WITH_TIMEOUT = "INTENT_ACTION_SET_WITH_TIMEOUT";
    public static final String INTENT_FLAG_ITEM_NAME = "itemName";
    public static final String INTENT_FLAG_ITEM_STATE = "itemState";
    public static final String INTENT_FLAG_ITEM_TIMEOUT_STATE = "itemTimeoutState";
    public static final String INTENT_FLAG_TIMEOUT = "timeout";

    public static final String INTENT_FLAG_INTRO_ONLY = "introOnly";

    /** preference related constants **/
    public static final String PREF_PREFIX = "pref_";
    public static final String PREF_SUFFIX_ENABLED = "_enabled";
    public static final String PREF_SUFFIX_ITEM = "_item";
    public static final String PREF_SUFFIX_AVERAGE = "_average";
    public static final String PREF_SUFFIX_INTERVAL = "_intervall";
    public static final String PREF_SUFFIX_SENSITIVITY = "_sensitivity";

    public static final String PREF_RESTART_ENABLED = "pref_restart_enabled";
    public static final String PREF_MAX_RESTARTS = "pref_max_restarts";
    public static final String PREF_LOAD_START_URL_ON_SCREENON = "pref_load_start_url_on_screenon";
    public static final String PREF_HW_ACCELERATED = "pref_hardware_accelerated";
    public static final String PREF_APP_VERSION = "pref_app_version";
    public static final String PREF_START_URL = "pref_start_url";
    public static final String PREF_POWER_SAVE_WARNING_SHOWN = "pref_powerSavingWarningShown";
    public static final String PREF_INTRO_SHOWN = "pref_intro_shown";
    public static final String PREF_MENU_POSITION = "pref_menu_position";
    public static final String PREF_SHOW_CONTEXT_MENU = "pref_show_context_menu";
    public static final String PREF_IMMERSIVE = "pref_immersive";
    public static final String PREF_TRACK_BROWSER_CONNECTION = "pref_track_browser_connection";
    public static final String PREF_LOG_BROWSER_MESSAGES = "pref_log_browser_messages";
    public static final String PREF_DESKTOP_MODE = "pref_desktop_mode";
    public static final String PREF_JAVASCRIPT = "pref_javascript";
    public static final String PREF_AUTOPLAY_VIDEO = "pref_autoplay_video";
    public static final String PREF_DISABLE_CACHE = "pref_disable_cache";
    public static final String PREF_PREVENT_DRAGGING = "pref_prevent_dragging";
    public static final String PREF_THEME = "pref_theme";
    public static final String PREF_TAKE_PIC_DELAY = "pref_take_pic_delay";
    public static final String PREF_JPEG_QUALITY = "pref_jpeg_quality";
    public static final String PREF_CMD_ITEM = "pref_command_item";
    public static final String PREF_CMD_LOG_SIZE = "pref_command_log_size";
    public static final String PREF_SERVER_URL = "pref_server_url";
    public static final String PREF_DEVICE_ADMIN = "pref_device_admin";
    public static final String PREF_ALLOW_WEBRTC = "pref_allow_webrtc";
    public static final String PREF_SHOW_ON_LOCK_SCREEN = "pref_show_on_lock_screen";

    public static final String PREF_USAGE_ENABLED = "pref_usage_enabled";
    public static final String PREF_USAGE_ITEM = "pref_usage_item";
    public static final String PREF_USAGE_TIMEOUT = "pref_usage_timeout";

    public static final String PREF_BATTERY_ENABLED = "pref_battery_enabled";
    public static final String PREF_BATTERY_ITEM = "pref_battery_item";
    public static final String PREF_BATTERY_CHARGING_ITEM = "pref_battery_charging_item";
    public static final String PREF_BATTERY_LEVEL_ITEM = "pref_battery_level_item";

    public static final String PREF_MOTION_DETECTION_ENABLED = "pref_motion_detection_enabled";
    public static final String PREF_MOTION_DETECTION_ITEM = "pref_motion_item";
    public static final String PREF_MOTION_DETECTION_PREVIEW = "pref_motion_detection_preview";
    public static final String PREF_MOTION_DETECTION_NEW_API = "pref_motion_detection_new_api";
    public static final String PREF_MOTION_DETECTION_GRANULARITY = "pref_motion_detection_granularity";
    public static final String PREF_MOTION_DETECTION_LENIENCY = "pref_motion_detection_leniency";
    public static final String PREF_MOTION_DETECTION_SLEEP = "pref_motion_detection_sleep";

    public static final String PREF_CAPTURE_SCREEN_ENABLED = "pref_capture_screen_enabled";
    public static final String PREF_ALLOW_MIXED_CONTENT = "pref_allow_mixed_content";

    public static final String PREF_CURRENT_URL_ENABLED = "pref_current_url_enabled";
    public static final String PREF_CURRENT_URL_ITEM = "pref_current_url_item";

    public static final String PREF_CONNECTED_ENABLED = "pref_connected_enabled";
    public static final String PREF_CONNECTED_INTERVAL = "pref_connected_interval";
    public static final String PREF_CONNECTED_ITEM = "pref_connected_item";
    public static final String PREF_STARTUP_ENABLED = "pref_startup_enabled";
    public static final String PREF_STARTUP_ITEM = "pref_startup_item";

    public static final String PREF_VOLUME_ENABLED = "pref_volume_enabled";
    public static final String PREF_VOLUME_ITEM = "pref_volume_item";

    public static final String PREF_DOCKING_STATE_ENABLED = "pref_docking_state_enabled";
    public static final String PREF_DOCKING_STATE_ITEM = "pref_docking_state_item";

    public static final String PREF_SCREEN_ITEM = "pref_screen_item";
    public static final String PREF_SCREEN_ENABLED = "pref_screen_enabled";
}
