package com.inclusive.assist;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SwipeListener implements View.OnTouchListener {

    private final GestureDetector gestureDetector;

    public SwipeListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetails());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    // These are the methods we can override in our Activity
    public void onSwipeLeft() {}
    public void onSwipeRight() {}
    public void onDoubleTap() {}

    private final class GestureDetails extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // Important: Must return true to detect other gestures
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Let normal clicks (buttons) work
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            SwipeListener.this.onDoubleTap();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight();
                        } else {
                            onSwipeLeft();
                        }
                        result = true;
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }
    }
}