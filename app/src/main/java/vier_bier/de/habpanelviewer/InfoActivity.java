package vier_bier.de.habpanelviewer;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;

/**
 * Status Information Activity showing interesting app or hardware details.
 */
public class InfoActivity extends Activity {
    private final ArrayList<InfoItem> list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        list.clear();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.info_main);

        final ListView listview = findViewById(R.id.info_listview);

        Bundle b = getIntent().getExtras();
        populateList(b);

        final InfoItemAdapter adapter = new InfoItemAdapter(this, list);

        listview.setAdapter(adapter);
    }

    private void populateList(Bundle b) {
        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        list.add(new InfoItem("HABPanelViewer", "Version: " + BuildConfig.VERSION_NAME + "\nBuild date: " + buildDate.toString()));

        if (b != null) {
            for (String key : b.keySet()) {
                String value = b.get(key).toString();
                list.add(new InfoItem(key, value));
            }
        }

        String camStr = "";
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camId : camManager.getCameraIdList()) {
                camStr += "Camera " + camId + ": ";

                CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                camStr += (hasFlash ? "has" : "no") + " flash, ";
                camStr += (facing == CameraCharacteristics.LENS_FACING_BACK ? "back" : "front") + "-facing\n";
            }
        } catch (CameraAccessException e) {
            camStr = "failed to access camera service: " + e.getMessage();
        }
        list.add(new InfoItem("Cameras", camStr.trim()));

    }

    private class InfoItem {
        private String name;
        private String value;

        public InfoItem(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    class InfoItemAdapter extends ArrayAdapter<InfoItem> {
        private final Activity context;

        public InfoItemAdapter(Activity context, ArrayList<InfoItem> items) {
            super(context, R.layout.list_info_item, items);
            this.context = context;
        }

        @NonNull
        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            LayoutInflater inflater = context.getLayoutInflater();
            View rowView= inflater.inflate(R.layout.list_info_item, null, true);

            TextView txtTitle = rowView.findViewById(R.id.name);
            TextView txtValue = rowView.findViewById(R.id.value);
            txtTitle.setText(getItem(position).name);
            txtValue.setText(getItem(position).value);

            return rowView;
        }
    }
}
