package ca.coffeeshopstudio.gaminginterfaceclient.views;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.AppCompatTextView;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import ca.coffeeshopstudio.gaminginterfaceclient.R;
import ca.coffeeshopstudio.gaminginterfaceclient.models.Command;
import ca.coffeeshopstudio.gaminginterfaceclient.models.Control;
import top.defaults.colorpicker.ColorPickerPopup;

/**
 Copyright [2019] [Terence Doerksen]

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
public class EditActivity extends AbstractGameActivity implements EditFragment.EditDialogListener, SeekBar.OnSeekBarChangeListener {
    private GestureDetector gd;
    private int currentApiVersion;
    private SeekBar width;
    private SeekBar height;
    private SeekBar fontSize;
    private boolean mode = false;
    private int minControlSize = 48;
    private int maxFontSize = 256;

    @SuppressLint("NewApi")
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (currentApiVersion >= Build.VERSION_CODES.KITKAT && hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        setupFullScreen();
        setupDoubleTap(EditActivity.this);
        setupControls();
        loadControls();
        toggleEditControls(View.GONE);
    }

    private void toggleEditControls(int visibility) {
        if (activeControl >= 0) {
            if (controls.get(activeControl) instanceof Button) {
                findViewById(R.id.seekFont).setVisibility(visibility);
            } else {
                findViewById(R.id.seekFont).setVisibility(View.GONE);
            }
            findViewById(R.id.seekHeight).setVisibility(visibility);
            findViewById(R.id.seekWidth).setVisibility(visibility);
        }
    }

    private void setupControls() {
        width = findViewById(R.id.seekWidth);
        height = findViewById(R.id.seekHeight);
        fontSize = findViewById(R.id.seekFont);

        findViewById(R.id.topLayout).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gd.onTouchEvent(event);
            }
        });
        findViewById(R.id.topLayout).setOnDragListener(new MyDragListener());

        ((Switch) findViewById(R.id.toggleMode)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mode = b;
                if (mode) {
                    toggleEditControls(View.GONE);
                    Toast.makeText(EditActivity.this, R.string.edit_activity_drag_mode, Toast.LENGTH_SHORT).show();
                } else if (activeControl > -1) {
                    toggleEditControls(View.VISIBLE);
                    Toast.makeText(EditActivity.this, R.string.edit_activity_detail_edit_mode, Toast.LENGTH_SHORT).show();
                }
            }
        });

        width.setMax(maxControlSize);
        height.setMax(maxControlSize);
        fontSize.setMax(maxFontSize);
        width.setOnSeekBarChangeListener(this);
        height.setOnSeekBarChangeListener(this);
        fontSize.setOnSeekBarChangeListener(this);

        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveScreen();
            }
        });

        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displayColorPicker(findViewById(R.id.topLayout));
            }
        });
    }

    private void displayColorPicker(final View view) {
        ColorDrawable color = (ColorDrawable) view.getBackground();
        new ColorPickerPopup.Builder(this)
                .initialColor(color.getColor()) // Set initial color
                .enableBrightness(true) // Enable brightness slider or not
                //.enableAlpha(true) // Enable alpha slider or not
                .okTitle(getString(R.string.color_picker_title))
                .cancelTitle(getString(android.R.string.cancel))
                .showIndicator(true)
                .showValue(true)
                .build()
                .show(view, new ColorPickerPopup.ColorPickerObserver() {
                    @Override
                    public void onColorPicked(int color) {
                        view.setBackgroundColor(color);
                    }
                });
    }

    private void saveScreen() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("gicsScreen", MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();

        ObjectMapper mapper = new ObjectMapper();

        //first we need to remove all existing controls
        Map<String,?> keys = prefs.getAll();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            if (entry.getKey().contains("control_")) {
                prefsEditor.remove(entry.getKey());
            }
        }

        ColorDrawable color = (ColorDrawable) findViewById(R.id.topLayout).getBackground();
        prefsEditor.putInt("background", color.getColor());
        try {
            int i = 0;
            for (View aview : controls ) {
                Control control = new Control();
                control.setCommand((Command) aview.getTag());
                control.setWidth(aview.getWidth());
                control.setLeft(aview.getX());
                control.setFontSize((int) ((TextView) aview).getTextSize());
                control.setText(((TextView) aview).getText().toString());
                control.setTop(aview.getY());
                control.setHeight(aview.getBottom());
                control.setFontColor(((TextView) aview).getTextColors().getDefaultColor());
                control.setPrimaryColor(primaryColors.get(i));
                control.setSecondaryColor(secondaryColors.get(i));
                if (aview instanceof Button)
                    control.setViewType(0);
                else
                    control.setViewType(1);
                String json = mapper.writeValueAsString(control);
                prefsEditor.putString("control_" + i, json);
                i++;
            }
            prefsEditor.apply();
            Toast.makeText(EditActivity.this, R.string.edit_activity_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(EditActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupFullScreen() {
        currentApiVersion = Build.VERSION.SDK_INT;
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (currentApiVersion >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(flags);
                    }
                }
            });
        }
    }

    private void setupDoubleTap(final Context context) {
        gd = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener(){
            //here is the method for double tap
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                showControlPopup();
                //addButton(context);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
    }

    private void showControlPopup() {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(EditActivity.this);

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(EditActivity.this, android.R.layout.simple_list_item_1);
        arrayAdapter.add("Button");
        arrayAdapter.add("Text");

        builderSingle.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        addButton(EditActivity.this);
                        break;
                    case 1:
                        addTextView(EditActivity.this);
                        break;
                }
            }
        });
        builderSingle.show();
    }

    private void addTextView(Context context) {
        FrameLayout layout = findViewById(R.id.topLayout);
        if (activeControl >= 0) {
            View previous = findViewById(controls.get(activeControl).getId());
            //reset the button color scheme to default if going away from one
            if (previous instanceof Button) {
                previous.setBackground(setButtonBackground(primaryColors.get(activeControl), secondaryColors.get(activeControl)));
            }
        }

        AppCompatTextView text = new AppCompatTextView(context);

        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(text, 24, maxControlSize, 2, TypedValue.COMPLEX_UNIT_SP);
        text.setText("New");
        text.setId(controls.size());
        text.setX(140);
        text.setY(200);

        //these do nothing, but necessary to prevent crashes
        primaryColors.add(Color.WHITE);
        secondaryColors.add(Color.WHITE);

        text.setOnClickListener(this);
        text.setOnTouchListener(new MyTouchListener());
        controls.add(text);
        activeControl = controls.size() - 1;
        width.setProgress(200);
        height.setProgress(120);
        toggleEditControls(View.VISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(text, lp);
    }

    private void addButton(Context context) {
        FrameLayout layout = findViewById(R.id.topLayout);
        if (activeControl >= 0) {
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{primaryColors.get(activeControl), secondaryColors.get(activeControl)});
            gd.setCornerRadius(3f);

            findViewById(controls.get(activeControl).getId()).setBackground(gd);
        }

        Button myButton = new Button(context);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.WHITE, Color.GRAY}
        );
        gd.setCornerRadius(3f);
        myButton.setBackground(gd);

        //myButton.setBackgroundResource(R.drawable.selected_button);
        myButton.setText("New");
        myButton.setId(controls.size());
        //myButton.setOnClickListener(this);
        myButton.setOnTouchListener(new MyTouchListener());
        myButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 48);

        myButton.setX(140);
        myButton.setY(200);
        controls.add(myButton);
        primaryColors.add(Color.WHITE);
        secondaryColors.add(Color.GRAY);
        activeControl = controls.size() - 1;
        width.setProgress(myButton.getWidth());
        height.setProgress(myButton.getHeight());
        fontSize.setProgress((int) myButton.getTextSize());
        toggleEditControls(View.VISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(myButton, lp);
    }

    private void displayEditDialog() {
        FragmentManager fm = getSupportFragmentManager();
        Command commandToSend = null;
        String buttonText = null;
        int fontColor = Color.BLACK;
        int secondaryColor = Color.WHITE;
        int primaryColor = Color.GRAY;
        if (activeControl >= 0) {
            fontColor = ((TextView) controls.get(activeControl)).getTextColors().getDefaultColor();
            primaryColor = primaryColors.get(activeControl);
            secondaryColor = secondaryColors.get(activeControl);
            buttonText = (String) ((TextView) controls.get(activeControl)).getText();
            commandToSend = ((Command) controls.get(activeControl).getTag());
        }
        EditFragment editNameDialogFragment = EditFragment.newInstance(getString(R.string.title_fragment_edit), buttonText, commandToSend, primaryColor, secondaryColor, fontColor, controls.get(activeControl));
        editNameDialogFragment.show(fm, "fragment_edit_name");
    }

    @Override
    protected void addDragDrop(View view) {
        view.setOnTouchListener(new MyTouchListener());
    }

    @Override
    public void onClick(View view) {
        if (activeControl == view.getId()) {
            displayEditDialog();
        } else {
            if (activeControl >= 0) {
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{primaryColors.get(activeControl), secondaryColors.get(activeControl)});
                gd.setCornerRadius(3f);

                controls.get(activeControl).setBackground(gd);
            }
            activeControl = view.getId();


            if (view instanceof Button)
                view.setBackground(setButtonBackground(secondaryColors.get(activeControl), primaryColors.get(activeControl)));

            width.setProgress(view.getWidth());
            height.setProgress(view.getHeight());
            fontSize.setProgress((int) ((TextView) view).getTextSize());
            toggleEditControls(View.VISIBLE);
        }
    }

    @Override
    public void onFinishEditDialog(Command command, String text, int primaryColor, int secondaryColor, int fontColor) {
        if (command == null && text.equals("DELETE")) {
            if (activeControl >= 0) {
                FrameLayout layout = findViewById(R.id.topLayout);
                layout.removeView(controls.get(activeControl));
                controls.remove(activeControl);
                primaryColors.remove(activeControl);
                secondaryColors.remove(activeControl);
                activeControl = -1;
                toggleEditControls(View.GONE);
            }
        } else {

            primaryColors.set(activeControl, primaryColor);
            secondaryColors.set(activeControl, secondaryColor);

            View view = controls.get(activeControl);

            if (view instanceof Button)
                view.setBackground(setButtonBackground(primaryColor, secondaryColor));

            ((TextView) view).setText(text);
            ((TextView) view).setTextColor(fontColor);
            view.setTag(command);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
        if (activeControl >= 0) {
            TextView view = ((TextView) controls.get(activeControl));

            int newWidth = view.getWidth();
            int newHeight = view.getHeight();
            int newFont = (int) view.getTextSize();
            switch (seekBar.getId()) {
                case R.id.seekHeight:
                    newHeight = value;
                    break;
                case R.id.seekWidth:
                    newWidth = value;
                    break;
                case R.id.seekFont:
                    newFont = value;
                    break;
            }
            if (newWidth >= minControlSize && newHeight >= minControlSize)
                view.setLayoutParams(new FrameLayout.LayoutParams(newWidth, newHeight));
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, newFont);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private final class MyTouchListener implements View.OnTouchListener {
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && mode) {
                ClipData data = ClipData.newPlainText("", "");
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(
                        view);
                view.startDrag(data, shadowBuilder, view, 0);
                view.setVisibility(View.INVISIBLE);
                return true;
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                onClick(view);
                return true;
            } else {
                //Log.d("drag", "onTouch: " + motionEvent.toString());
                return false;
            }
        }
    }

    private final class MyDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    break;
                case DragEvent.ACTION_DROP:
                    View view = (View) event.getLocalState();
                    float x = event.getX();
                    float y = event.getY();
                    view.setX(x-(view.getWidth()/2));
                    view.setY(y-(view.getHeight()/2));
                    view.setVisibility(View.VISIBLE);
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                default:
                    break;
            }
            return true;
        }
    }
}
