/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.kunzisoft.switchdatetimesample;

import android.animation.ObjectAnimator;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.wdullaer.materialdatetimepicker.R;
import com.wdullaer.materialdatetimepicker.Utils;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout.OnValueSelectedListener;
import com.wdullaer.materialdatetimepicker.time.TimePickerController;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog.Version;
import com.wdullaer.materialdatetimepicker.time.Timepoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Dialog to set a time.
 */
public class NNTimePickerDialog extends Fragment implements
        OnValueSelectedListener, TimePickerController {
    private static final String TAG = "NTimePickerDialog";

    private static final String KEY_INITIAL_TIME = "initial_time";
    private static final String KEY_IS_24_HOUR_VIEW = "is_24_hour_view";
    private static final String KEY_CURRENT_ITEM_SHOWING = "current_item_showing";
    private static final String KEY_IN_KB_MODE = "in_kb_mode";
    private static final String KEY_TYPED_TIMES = "typed_times";
    private static final String KEY_SELECTABLE_TIMES = "selectable_times";
    private static final String KEY_MIN_TIME = "min_time";
    private static final String KEY_MAX_TIME = "max_time";

    public static final int HOUR_INDEX = 0;
    public static final int MINUTE_INDEX = 1;
    public static final int SECOND_INDEX = 2;

    // Delay before starting the pulse animation, in ms.
    private static final int PULSE_ANIMATOR_DELAY = 300;

    private OnTimeSetListener mCallback;

    private Button mCancelButton;
    private Button mOkButton;
    private TextView mHourView;
    private TextView mHourSpaceView;
    private TextView mMinuteView;
    private TextView mMinuteSpaceView;
    private RadialPickerLayout mTimePicker;

    private int mSelectedColor;
    private int mUnselectedColor;

    private boolean mAllowAutoAdvance;
    private Timepoint mInitialTime;
    private Timepoint[] mSelectableTimes;
    private Timepoint mMinTime;
    private Timepoint mMaxTime;

    // For hardware IME input.
    private char mPlaceholderText;
    private String mDoublePlaceholderText;
    private String mDeletedKeyFormat;
    private boolean mInKbMode;
    private ArrayList<Integer> mTypedTimes;
    private Node mLegalTimesTree;

    // Accessibility strings.
    private String mHourPickerDescription;
    private String mSelectHours;
    private String mMinutePickerDescription;
    private String mSelectMinutes;
    private String mSecondPickerDescription;

    /**
     * The callback interface used to indicate the user is done filling in
     * the time (they clicked on the 'Set' button).
     */
    public interface OnTimeSetListener {

        /**
         * @param view      The view associated with this listener.
         * @param hourOfDay The hour that was set.
         * @param minute    The minute that was set.
         * @param second    The second that was set
         */
        void onTimeSet(NNTimePickerDialog view, int hourOfDay, int minute, int second);
    }

    public NNTimePickerDialog() {
        // Empty constructor required for dialog fragment.
    }

    public static NNTimePickerDialog newInstance(OnTimeSetListener callback,
                                                 int hourOfDay, int minute, int second) {
        NNTimePickerDialog ret = new NNTimePickerDialog();
        ret.initialize(callback, hourOfDay, minute, second);
        return ret;
    }

    public static NNTimePickerDialog newInstance(OnTimeSetListener callback,
                                                 int hourOfDay, int minute) {
        return NNTimePickerDialog.newInstance(callback, hourOfDay, minute, 0);
    }

    @SuppressWarnings("unused")
    public static NNTimePickerDialog newInstance(OnTimeSetListener callback) {
        Calendar now = Calendar.getInstance();
        return NNTimePickerDialog.newInstance(callback, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    public void initialize(OnTimeSetListener callback,
                           int hourOfDay, int minute, int second) {
        mCallback = callback;

        mInitialTime = new Timepoint(hourOfDay, minute, second);
        mInKbMode = false;
    }

    @Override
    public boolean isThemeDark() {
        return false;
    }

    @Override
    public boolean is24HourMode() {
        return true;
    }

    @Override
    public int getAccentColor() {
        return 0;
    }

    @SuppressWarnings("unused")
    public void setMinTime(int hour, int minute, int second) {
        setMinTime(new Timepoint(hour, minute, second));
    }

    public void setMinTime(Timepoint minTime) {
        if (mMaxTime != null && minTime.compareTo(mMaxTime) > 0)
            throw new IllegalArgumentException("Minimum time must be smaller than the maximum time");
        mMinTime = minTime;
    }

    @SuppressWarnings("unused")
    public void setMaxTime(int hour, int minute, int second) {
        setMaxTime(new Timepoint(hour, minute, second));
    }

    public void setMaxTime(Timepoint maxTime) {
        if (mMinTime != null && maxTime.compareTo(mMinTime) < 0)
            throw new IllegalArgumentException("Maximum time must be greater than the minimum time");
        mMaxTime = maxTime;
    }

    @SuppressWarnings("unused")
    public void setSelectableTimes(Timepoint[] selectableTimes) {
        mSelectableTimes = selectableTimes;
        Arrays.sort(mSelectableTimes);
    }

    /**
     * Set the interval for selectable times in the NTimePickerDialog
     * This is a convenience wrapper around setSelectableTimes
     * The interval for all three time components can be set independently
     *
     * @param hourInterval   The interval between 2 selectable hours ([1,24])
     * @param minuteInterval The interval between 2 selectable minutes ([1,60])
     * @param secondInterval The interval between 2 selectable seconds ([1,60])
     */
    public void setTimeInterval(@IntRange(from = 1, to = 24) int hourInterval,
                                @IntRange(from = 1, to = 60) int minuteInterval,
                                @IntRange(from = 1, to = 60) int secondInterval) {
        List<Timepoint> timepoints = new ArrayList<>();

        int hour = 0;
        while (hour < 24) {
            int minute = 0;
            while (minute < 60) {
                int second = 0;
                while (second < 60) {
                    timepoints.add(new Timepoint(hour, minute, second));
                    second += secondInterval;
                }
                minute += minuteInterval;
            }
            hour += hourInterval;
        }
        setSelectableTimes(timepoints.toArray(new Timepoint[timepoints.size()]));
    }

    /**
     * Set the interval for selectable times in the NTimePickerDialog
     * This is a convenience wrapper around setSelectableTimes
     * The interval for all three time components can be set independently
     *
     * @param hourInterval   The interval between 2 selectable hours ([1,24])
     * @param minuteInterval The interval between 2 selectable minutes ([1,60])
     */
    public void setTimeInterval(@IntRange(from = 1, to = 24) int hourInterval,
                                @IntRange(from = 1, to = 60) int minuteInterval) {
        setTimeInterval(hourInterval, minuteInterval, 1);
    }

    /**
     * Set the interval for selectable times in the NTimePickerDialog
     * This is a convenience wrapper around setSelectableTimes
     * The interval for all three time components can be set independently
     *
     * @param hourInterval The interval between 2 selectable hours ([1,24])
     */
    @SuppressWarnings("unused")
    public void setTimeInterval(@IntRange(from = 1, to = 24) int hourInterval) {
        setTimeInterval(hourInterval, 1);
    }

    public void setOnTimeSetListener(OnTimeSetListener callback) {
        mCallback = callback;
    }

    public void setStartTime(int hourOfDay, int minute, int second) {
        mInitialTime = roundToNearest(new Timepoint(hourOfDay, minute, second));
        mInKbMode = false;
    }

    @SuppressWarnings("unused")
    public void setStartTime(int hourOfDay, int minute) {
        setStartTime(hourOfDay, minute, 0);
    }

    @Override
    public Version getVersion() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? Version.VERSION_1 : Version.VERSION_2;
    }

    @Override
    public void tryVibrate() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_INITIAL_TIME)
                && savedInstanceState.containsKey(KEY_IS_24_HOUR_VIEW)) {
            mInitialTime = savedInstanceState.getParcelable(KEY_INITIAL_TIME);
            mInKbMode = savedInstanceState.getBoolean(KEY_IN_KB_MODE);
            mSelectableTimes = (Timepoint[]) savedInstanceState.getParcelableArray(KEY_SELECTABLE_TIMES);
            mMinTime = savedInstanceState.getParcelable(KEY_MIN_TIME);
            mMaxTime = savedInstanceState.getParcelable(KEY_MAX_TIME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        int viewRes = R.layout.mdtp_time_picker_dialog_v2;
        View view = inflater.inflate(viewRes, container, false);
        KeyboardListener keyboardListener = new KeyboardListener();
        view.findViewById(R.id.mdtp_time_picker_dialog).setOnKeyListener(keyboardListener);

        Resources res = getResources();
        Context context = getActivity();
        mHourPickerDescription = res.getString(R.string.mdtp_hour_picker_description);
        mSelectHours = res.getString(R.string.mdtp_select_hours);
        mMinutePickerDescription = res.getString(R.string.mdtp_minute_picker_description);
        mSelectMinutes = res.getString(R.string.mdtp_select_minutes);
        mSecondPickerDescription = res.getString(R.string.mdtp_second_picker_description);
        mSelectedColor = ContextCompat.getColor(context, R.color.mdtp_white);
        mUnselectedColor = ContextCompat.getColor(context, R.color.mdtp_accent_color_focused);

        mHourView = (TextView) view.findViewById(R.id.mdtp_hours);
        mHourView.setOnKeyListener(keyboardListener);
        mHourSpaceView = (TextView) view.findViewById(R.id.mdtp_hour_space);
        mMinuteSpaceView = (TextView) view.findViewById(R.id.mdtp_minutes_space);
        mMinuteView = (TextView) view.findViewById(R.id.mdtp_minutes);
        mMinuteView.setOnKeyListener(keyboardListener);

        if (mTimePicker != null) {
            mInitialTime = new Timepoint(mTimePicker.getHours(), mTimePicker.getMinutes(), mTimePicker.getSeconds());
        }

        mInitialTime = roundToNearest(mInitialTime);

        mTimePicker = (RadialPickerLayout) view.findViewById(R.id.mdtp_time_picker);
        mTimePicker.setOnValueSelectedListener(this);
        mTimePicker.setOnKeyListener(keyboardListener);
        mTimePicker.initialize(getActivity(), this, mInitialTime, true);

        int currentItemShowing = HOUR_INDEX;
        if (savedInstanceState != null &&
                savedInstanceState.containsKey(KEY_CURRENT_ITEM_SHOWING)) {
            currentItemShowing = savedInstanceState.getInt(KEY_CURRENT_ITEM_SHOWING);
        }
        setCurrentItemShowing(currentItemShowing, false, true, true);
        mTimePicker.invalidate();

        mHourView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setCurrentItemShowing(HOUR_INDEX, true, false, true);
            }
        });
        mMinuteView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setCurrentItemShowing(MINUTE_INDEX, true, false, true);
            }
        });

        mOkButton = (Button) view.findViewById(R.id.mdtp_ok);
        mOkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mInKbMode && isTypedTimeFullyLegal()) {
                    finishKbMode(false);
                }
                notifyOnDateListener();
            }
        });
        mOkButton.setOnKeyListener(keyboardListener);

        mCancelButton = (Button) view.findViewById(R.id.mdtp_cancel);
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        // center first separator
        RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        );
        paramsSeparator.addRule(RelativeLayout.CENTER_IN_PARENT);
        TextView separatorView = (TextView) view.findViewById(R.id.mdtp_separator);
        separatorView.setLayoutParams(paramsSeparator);

        mAllowAutoAdvance = true;
        setHour(mInitialTime.getHour(), true);
        setMinute(mInitialTime.getMinute());

        // Set up for keyboard mode.
        mDoublePlaceholderText = res.getString(R.string.mdtp_time_placeholder);
        mDeletedKeyFormat = res.getString(R.string.mdtp_deleted_key);
        mPlaceholderText = mDoublePlaceholderText.charAt(0);
        generateLegalTimesTree();
        if (mInKbMode) {
            mTypedTimes = savedInstanceState.getIntegerArrayList(KEY_TYPED_TIMES);
            tryStartingKbMode(-1);
            mHourView.invalidate();
        } else if (mTypedTimes == null) {
            mTypedTimes = new ArrayList<>();
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mTimePicker != null) {
            outState.putParcelable(KEY_INITIAL_TIME, mTimePicker.getTime());
            outState.putBoolean(KEY_IS_24_HOUR_VIEW, true);
            outState.putInt(KEY_CURRENT_ITEM_SHOWING, mTimePicker.getCurrentItemShowing());
            outState.putBoolean(KEY_IN_KB_MODE, mInKbMode);
            if (mInKbMode) {
                outState.putIntegerArrayList(KEY_TYPED_TIMES, mTypedTimes);
            }
            outState.putParcelableArray(KEY_SELECTABLE_TIMES, mSelectableTimes);
            outState.putParcelable(KEY_MIN_TIME, mMinTime);
            outState.putParcelable(KEY_MAX_TIME, mMaxTime);
        }
    }

    /**
     * Called by the picker for updating the header display.
     */
    @Override
    public void onValueSelected(Timepoint newValue) {
        setHour(newValue.getHour(), false);
        mTimePicker.setContentDescription(mHourPickerDescription + ": " + newValue.getHour());
        setMinute(newValue.getMinute());
        mTimePicker.setContentDescription(mMinutePickerDescription + ": " + newValue.getMinute());
        mTimePicker.setContentDescription(mSecondPickerDescription + ": " + newValue.getSecond());
    }

    @Override
    public void advancePicker(int index) {
        if (!mAllowAutoAdvance) return;
        if (index == HOUR_INDEX && true) {
            setCurrentItemShowing(MINUTE_INDEX, true, true, false);

            String announcement = mSelectHours + ". " + mTimePicker.getMinutes();
            Utils.tryAccessibilityAnnounce(mTimePicker, announcement);
        } else if (index == MINUTE_INDEX && false) {
            setCurrentItemShowing(SECOND_INDEX, true, true, false);

            String announcement = mSelectMinutes + ". " + mTimePicker.getSeconds();
            Utils.tryAccessibilityAnnounce(mTimePicker, announcement);
        }
    }

    @Override
    public void enablePicker() {
        if (!isTypedTimeFullyLegal()) mTypedTimes.clear();
        finishKbMode(true);
    }

    public boolean isOutOfRange(Timepoint current) {
        if (mMinTime != null && mMinTime.compareTo(current) > 0) return true;

        if (mMaxTime != null && mMaxTime.compareTo(current) < 0) return true;

        if (mSelectableTimes != null) return !Arrays.asList(mSelectableTimes).contains(current);

        return false;
    }

    @Override
    public boolean isOutOfRange(Timepoint current, int index) {
        if (current == null) return false;

        if (index == HOUR_INDEX) {
            if (mMinTime != null && mMinTime.getHour() > current.getHour()) return true;

            if (mMaxTime != null && mMaxTime.getHour() + 1 <= current.getHour()) return true;

            if (mSelectableTimes != null) {
                for (Timepoint t : mSelectableTimes) {
                    if (t.getHour() == current.getHour()) return false;
                }
                return true;
            }

            return false;
        } else if (index == MINUTE_INDEX) {
            if (mMinTime != null) {
                Timepoint roundedMin = new Timepoint(mMinTime.getHour(), mMinTime.getMinute());
                if (roundedMin.compareTo(current) > 0) return true;
            }

            if (mMaxTime != null) {
                Timepoint roundedMax = new Timepoint(mMaxTime.getHour(), mMaxTime.getMinute(), 59);
                if (roundedMax.compareTo(current) < 0) return true;
            }

            if (mSelectableTimes != null) {
                for (Timepoint t : mSelectableTimes) {
                    if (t.getHour() == current.getHour() && t.getMinute() == current.getMinute())
                        return false;
                }
                return true;
            }

            return false;
        } else return isOutOfRange(current);
    }

    @Override
    public boolean isAmDisabled() {
        return true;
    }

    @Override
    public boolean isPmDisabled() {
        return true;
    }

    /**
     * Round a given Timepoint to the nearest valid Timepoint
     *
     * @param time Timepoint - The timepoint to round
     * @return Timepoint - The nearest valid Timepoint
     */
    private Timepoint roundToNearest(@NonNull Timepoint time) {
        return roundToNearest(time, null);
    }

    @Override
    public Timepoint roundToNearest(@NonNull Timepoint time, @Nullable Timepoint.TYPE type) {

        if (mMinTime != null && mMinTime.compareTo(time) > 0) return mMinTime;

        if (mMaxTime != null && mMaxTime.compareTo(time) < 0) return mMaxTime;
        if (mSelectableTimes != null) {
            int currentDistance = Integer.MAX_VALUE;
            Timepoint output = time;
            for (Timepoint t : mSelectableTimes) {
                // type == null: no restrictions
                // type == HOUR: do not change the hour
                if (type == Timepoint.TYPE.HOUR && t.getHour() != time.getHour()) continue;
                // type == MINUTE: do not change hour or minute
                if (type == Timepoint.TYPE.MINUTE && t.getHour() != time.getHour() && t.getMinute() != time.getMinute())
                    continue;
                // type == SECOND: cannot change anything, return input
                if (type == Timepoint.TYPE.SECOND) return time;
                int newDistance = Math.abs(t.compareTo(time));
                if (newDistance < currentDistance) {
                    currentDistance = newDistance;
                    output = t;
                } else break;
            }
            return output;
        }

        return time;
    }

    private void setHour(int value, boolean announce) {
        String format;
        format = "%02d";

        CharSequence text = String.format(format, value);
        mHourView.setText(text);
        mHourSpaceView.setText(text);
        if (announce) {
            Utils.tryAccessibilityAnnounce(mTimePicker, text);
        }
    }

    private void setMinute(int value) {
        if (value == 60) {
            value = 0;
        }
        CharSequence text = String.format(Locale.getDefault(), "%02d", value);
        Utils.tryAccessibilityAnnounce(mTimePicker, text);
        mMinuteView.setText(text);
        mMinuteSpaceView.setText(text);
    }

    // Show either Hours or Minutes.
    private void setCurrentItemShowing(int index, boolean animateCircle, boolean delayLabelAnimate,
                                       boolean announce) {
        mTimePicker.setCurrentItemShowing(index, animateCircle);

        TextView labelToAnimate;
        switch (index) {
            case HOUR_INDEX:
                int hours = mTimePicker.getHours();
                mTimePicker.setContentDescription(mHourPickerDescription + ": " + hours);
                if (announce) {
                    Utils.tryAccessibilityAnnounce(mTimePicker, mSelectHours);
                }
                labelToAnimate = mHourView;
                break;
            default:
                int minutes = mTimePicker.getMinutes();
                mTimePicker.setContentDescription(mMinutePickerDescription + ": " + minutes);
                if (announce) {
                    Utils.tryAccessibilityAnnounce(mTimePicker, mSelectMinutes);
                }
                labelToAnimate = mMinuteView;
        }

        int hourColor = (index == HOUR_INDEX) ? mSelectedColor : mUnselectedColor;
        int minuteColor = (index == MINUTE_INDEX) ? mSelectedColor : mUnselectedColor;
        mHourView.setTextColor(hourColor);
        mMinuteView.setTextColor(minuteColor);

        ObjectAnimator pulseAnimator = Utils.getPulseAnimator(labelToAnimate, 0.85f, 1.1f);
        if (delayLabelAnimate) {
            pulseAnimator.setStartDelay(PULSE_ANIMATOR_DELAY);
        }
        pulseAnimator.start();
    }

    /**
     * For keyboard mode, processes key events.
     *
     * @param keyCode the pressed key.
     * @return true if the key was successfully processed, false otherwise.
     */
    private boolean processKeyUp(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (mInKbMode) {
                if (isTypedTimeFullyLegal()) {
                    finishKbMode(true);
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mInKbMode) {
                if (!isTypedTimeFullyLegal()) {
                    return true;
                }
                finishKbMode(false);
            }
            if (mCallback != null) {
                mCallback.onTimeSet(this,
                        mTimePicker.getHours(), mTimePicker.getMinutes(), mTimePicker.getSeconds());
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (mInKbMode) {
                if (!mTypedTimes.isEmpty()) {
                    int deleted = deleteLastTypedKey();
                    String deletedKeyStr;
                    deletedKeyStr = String.format("%d", getValFromKeyCode(deleted));
                    Utils.tryAccessibilityAnnounce(mTimePicker,
                            String.format(mDeletedKeyFormat, deletedKeyStr));
                    updateDisplay(true);
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_1
                || keyCode == KeyEvent.KEYCODE_2 || keyCode == KeyEvent.KEYCODE_3
                || keyCode == KeyEvent.KEYCODE_4 || keyCode == KeyEvent.KEYCODE_5
                || keyCode == KeyEvent.KEYCODE_6 || keyCode == KeyEvent.KEYCODE_7
                || keyCode == KeyEvent.KEYCODE_8 || keyCode == KeyEvent.KEYCODE_9) {
            if (!mInKbMode) {
                if (mTimePicker == null) {
                    // Something's wrong, because time picker should definitely not be null.
                    Log.e(TAG, "Unable to initiate keyboard mode, TimePicker was null.");
                    return true;
                }
                mTypedTimes.clear();
                tryStartingKbMode(keyCode);
                return true;
            }
            // We're already in keyboard mode.
            if (addKeyIfLegal(keyCode)) {
                updateDisplay(false);
            }
            return true;
        }
        return false;
    }

    /**
     * Try to start keyboard mode with the specified key, as long as the timepicker is not in the
     * middle of a touch-event.
     *
     * @param keyCode The key to use as the first press. Keyboard mode will not be started if the
     *                key is not legal to start with. Or, pass in -1 to get into keyboard mode without a starting
     *                key.
     */
    private void tryStartingKbMode(int keyCode) {
        if (mTimePicker.trySettingInputEnabled(false) &&
                (keyCode == -1 || addKeyIfLegal(keyCode))) {
            mInKbMode = true;
            mOkButton.setEnabled(false);
            updateDisplay(false);
        }
    }

    private boolean addKeyIfLegal(int keyCode) {
        // If we're in 24hour mode, we'll need to check if the input is full. If in AM/PM mode,
        // we'll need to see if AM/PM have been typed.
        int textSize = 6;
        textSize = 4;
        if ((mTypedTimes.size() == textSize)) {
            return false;
        }

        mTypedTimes.add(keyCode);
        if (!isTypedTimeLegalSoFar()) {
            deleteLastTypedKey();
            return false;
        }

        int val = getValFromKeyCode(keyCode);
        Utils.tryAccessibilityAnnounce(mTimePicker, String.format(Locale.getDefault(), "%d", val));
        // Automatically fill in 0's if AM or PM was legally entered.
        if (isTypedTimeFullyLegal()) {
            mOkButton.setEnabled(true);
        }

        return true;
    }

    /**
     * Traverse the tree to see if the keys that have been typed so far are legal as is,
     * or may become legal as more keys are typed (excluding backspace).
     */
    private boolean isTypedTimeLegalSoFar() {
        Node node = mLegalTimesTree;
        for (int keyCode : mTypedTimes) {
            node = node.canReach(keyCode);
            if (node == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the time that has been typed so far is completely legal, as is.
     */
    private boolean isTypedTimeFullyLegal() {
        // For 24-hour mode, the time is legal if the hours and minutes are each legal. Note:
        // getEnteredTime() will ONLY call isTypedTimeFullyLegal() when NOT in 24hour mode.
        int[] values = getEnteredTime(null);
        return (values[0] >= 0 && values[1] >= 0 && values[1] < 60 && values[2] >= 0 && values[2] < 60);
    }

    private int deleteLastTypedKey() {
        int deleted = mTypedTimes.remove(mTypedTimes.size() - 1);
        if (!isTypedTimeFullyLegal()) {
            mOkButton.setEnabled(false);
        }
        return deleted;
    }

    /**
     * Get out of keyboard mode. If there is nothing in typedTimes, revert to TimePicker's time.
     *
     * @param updateDisplays If true, update the displays with the relevant time.
     */
    private void finishKbMode(boolean updateDisplays) {
        mInKbMode = false;
        if (!mTypedTimes.isEmpty()) {
            int values[] = getEnteredTime(null);
            mTimePicker.setTime(new Timepoint(values[0], values[1], values[2]));
            mTypedTimes.clear();
        }
        if (updateDisplays) {
            updateDisplay(false);
            mTimePicker.trySettingInputEnabled(true);
        }
    }

    /**
     * Update the hours, minutes, seconds and AM/PM displays with the typed times. If the typedTimes
     * is empty, either show an empty display (filled with the placeholder text), or update from the
     * timepicker's values.
     *
     * @param allowEmptyDisplay if true, then if the typedTimes is empty, use the placeholder text.
     *                          Otherwise, revert to the timepicker's values.
     */
    private void updateDisplay(boolean allowEmptyDisplay) {
        if (!allowEmptyDisplay && mTypedTimes.isEmpty()) {
            int hour = mTimePicker.getHours();
            int minute = mTimePicker.getMinutes();
            setHour(hour, true);
            setMinute(minute);
            setCurrentItemShowing(mTimePicker.getCurrentItemShowing(), true, true, true);
            mOkButton.setEnabled(true);
        } else {
            Boolean[] enteredZeros = {false, false, false};
            int[] values = getEnteredTime(enteredZeros);
            String hourFormat = enteredZeros[0] ? "%02d" : "%2d";
            String minuteFormat = (enteredZeros[1]) ? "%02d" : "%2d";
            String hourStr = (values[0] == -1) ? mDoublePlaceholderText :
                    String.format(hourFormat, values[0]).replace(' ', mPlaceholderText);
            String minuteStr = (values[1] == -1) ? mDoublePlaceholderText :
                    String.format(minuteFormat, values[1]).replace(' ', mPlaceholderText);
            mHourView.setText(hourStr);
            mHourSpaceView.setText(hourStr);
            mHourView.setTextColor(mUnselectedColor);
            mMinuteView.setText(minuteStr);
            mMinuteSpaceView.setText(minuteStr);
            mMinuteView.setTextColor(mUnselectedColor);
        }
    }

    private static int getValFromKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_0:
                return 0;
            case KeyEvent.KEYCODE_1:
                return 1;
            case KeyEvent.KEYCODE_2:
                return 2;
            case KeyEvent.KEYCODE_3:
                return 3;
            case KeyEvent.KEYCODE_4:
                return 4;
            case KeyEvent.KEYCODE_5:
                return 5;
            case KeyEvent.KEYCODE_6:
                return 6;
            case KeyEvent.KEYCODE_7:
                return 7;
            case KeyEvent.KEYCODE_8:
                return 8;
            case KeyEvent.KEYCODE_9:
                return 9;
            default:
                return -1;
        }
    }

    /**
     * Get the currently-entered time, as integer values of the hours, minutes and seconds typed.
     *
     * @param enteredZeros A size-2 boolean array, which the caller should initialize, and which
     *                     may then be used for the caller to know whether zeros had been explicitly entered as either
     *                     hours of minutes. This is helpful for deciding whether to show the dashes, or actual 0's.
     * @return A size-3 int array. The first value will be the hours, the second value will be the
     * minutes, and the third will be either NTimePickerDialog.AM or NTimePickerDialog.PM.
     */
    private int[] getEnteredTime(Boolean[] enteredZeros) {
        int amOrPm = -1;
        int startIndex = 1;
        int minute = -1;
        int hour = -1;
        int second = 0;
        int shift = false ? 2 : 0;
        for (int i = startIndex; i <= mTypedTimes.size(); i++) {
            int val = getValFromKeyCode(mTypedTimes.get(mTypedTimes.size() - i));
            if (false) {
                if (i == startIndex) {
                    second = val;
                } else if (i == startIndex + 1) {
                    second += 10 * val;
                    if (enteredZeros != null && val == 0) {
                        enteredZeros[2] = true;
                    }
                }
            }
            if (i == startIndex + shift) {
                minute = val;
            } else if (i == startIndex + shift + 1) {
                minute += 10 * val;
                if (enteredZeros != null && val == 0) {
                    enteredZeros[1] = true;
                }
            } else if (i == startIndex + shift + 2) {
                hour = val;
            } else if (i == startIndex + shift + 3) {
                hour += 10 * val;
                if (enteredZeros != null && val == 0) {
                    enteredZeros[0] = true;
                }
            }
        }

        return new int[]{hour, minute, second, amOrPm};
    }

    /**
     * Create a tree for deciding what keys can legally be typed.
     */
    private void generateLegalTimesTree() {
        // Create a quick cache of numbers to their keycodes.
        int k0 = KeyEvent.KEYCODE_0;
        int k1 = KeyEvent.KEYCODE_1;
        int k2 = KeyEvent.KEYCODE_2;
        int k3 = KeyEvent.KEYCODE_3;
        int k4 = KeyEvent.KEYCODE_4;
        int k5 = KeyEvent.KEYCODE_5;
        int k6 = KeyEvent.KEYCODE_6;
        int k7 = KeyEvent.KEYCODE_7;
        int k8 = KeyEvent.KEYCODE_8;
        int k9 = KeyEvent.KEYCODE_9;

        // The root of the tree doesn't contain any numbers.
        mLegalTimesTree = new Node();

        // We'll be re-using these nodes, so we'll save them.
        Node minuteFirstDigit = new Node(k0, k1, k2, k3, k4, k5);
        Node minuteSecondDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
        // The first digit must be followed by the second digit.
        minuteFirstDigit.addChild(minuteSecondDigit);

        // The first digit may be 0-1.
        Node firstDigit = new Node(k0, k1);
        mLegalTimesTree.addChild(firstDigit);

        // When the first digit is 0-1, the second digit may be 0-5.
        Node secondDigit = new Node(k0, k1, k2, k3, k4, k5);
        firstDigit.addChild(secondDigit);
        // We may now be followed by the first minute digit. E.g. 00:09, 15:58.
        secondDigit.addChild(minuteFirstDigit);

        // When the first digit is 0-1, and the second digit is 0-5, the third digit may be 6-9.
        Node thirdDigit = new Node(k6, k7, k8, k9);
        // The time must now be finished. E.g. 0:55, 1:08.
        secondDigit.addChild(thirdDigit);

        // When the first digit is 0-1, the second digit may be 6-9.
        secondDigit = new Node(k6, k7, k8, k9);
        firstDigit.addChild(secondDigit);
        // We must now be followed by the first minute digit. E.g. 06:50, 18:20.
        secondDigit.addChild(minuteFirstDigit);

        // The first digit may be 2.
        firstDigit = new Node(k2);
        mLegalTimesTree.addChild(firstDigit);

        // When the first digit is 2, the second digit may be 0-3.
        secondDigit = new Node(k0, k1, k2, k3);
        firstDigit.addChild(secondDigit);
        // We must now be followed by the first minute digit. E.g. 20:50, 23:09.
        secondDigit.addChild(minuteFirstDigit);

        // When the first digit is 2, the second digit may be 4-5.
        secondDigit = new Node(k4, k5);
        firstDigit.addChild(secondDigit);
        // We must now be followd by the last minute digit. E.g. 2:40, 2:53.
        secondDigit.addChild(minuteSecondDigit);

        // The first digit may be 3-9.
        firstDigit = new Node(k3, k4, k5, k6, k7, k8, k9);
        mLegalTimesTree.addChild(firstDigit);
        // We must now be followed by the first minute digit. E.g. 3:57, 8:12.
        firstDigit.addChild(minuteFirstDigit);
    }

    /**
     * Simple node class to be used for traversal to check for legal times.
     * mLegalKeys represents the keys that can be typed to get to the node.
     * mChildren are the children that can be reached from this node.
     */
    private static class Node {
        private int[] mLegalKeys;
        private ArrayList<Node> mChildren;

        public Node(int... legalKeys) {
            mLegalKeys = legalKeys;
            mChildren = new ArrayList<>();
        }

        public void addChild(Node child) {
            mChildren.add(child);
        }

        public boolean containsKey(int key) {
            for (int legalKey : mLegalKeys) {
                if (legalKey == key) return true;
            }
            return false;
        }

        public Node canReach(int key) {
            if (mChildren == null) {
                return null;
            }
            for (Node child : mChildren) {
                if (child.containsKey(key)) {
                    return child;
                }
            }
            return null;
        }
    }

    private class KeyboardListener implements OnKeyListener {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return processKeyUp(keyCode);
            }
            return false;
        }
    }

    public void notifyOnDateListener() {
        if (mCallback != null) {
            mCallback.onTimeSet(this, mTimePicker.getHours(), mTimePicker.getMinutes(), mTimePicker.getSeconds());
        }
    }

    public Timepoint getSelectedTime() {
        return mTimePicker.getTime();
    }
}
