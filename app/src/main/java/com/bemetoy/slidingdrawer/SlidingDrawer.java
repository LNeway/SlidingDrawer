package com.bemetoy.slidingdrawer;


import android.content.Context;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ValueAnimator;

/**
 * Created by Tom on 2016/3/1.
 */
public class SlidingDrawer extends ViewGroup {

    public static final int DRAWER_OPEN = 0;
    public static final int DRAWER_FILLING = 1;
    public static final int DRAWER_CLOSED = 2;
    private static final String TAG = SlidingDrawer.class.getSimpleName();
    private View handle;
    private View content;
    private int handleId;
    private int contentId;
    private OnDrawerStatusChangeListener onDrawerStatusChangeListener;
    // 记录消息按钮在拖动之前的位置
    private float originalX = 0;
    private int preMovePosition; //记录上次滑动事件的位置，这个值会随着ACTION_MOVE事件的触发不断改变
    private volatile int currentStatus = DRAWER_OPEN;
    private int left;  //记录布局时，handle的leftmarigin
    public SlidingDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingDrawer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BMSlidingDrawer, defStyleAttr, 0);

        int handleId = a.getResourceId(
                R.styleable.BMSlidingDrawer_handle, 0);
        if (handleId == 0) {
            throw new IllegalArgumentException(
                    "The handle attribute is required and must refer "
                            + "to a valid child.");
        }

        int contentId = a.getResourceId(
                R.styleable.BMSlidingDrawer_content, 0);
        if (contentId == 0) {
            throw new IllegalArgumentException(
                    "The content attribute is required and must refer "
                            + "to a valid child.");
        }

        if (handleId == contentId) {
            throw new IllegalArgumentException(
                    "The content and handle attributes must refer "
                            + "to different children.");
        }
        a.recycle();
        this.handleId = handleId;
        this.contentId = contentId;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        handle = findViewById(handleId);
        if (handle == null) {
            throw new IllegalArgumentException(
                    "The handle attribute is must refer to an"
                            + " existing child.");
        }
        content = findViewById(contentId);
        if (content == null) {
            throw new IllegalArgumentException(
                    "The content attribute is must refer to an"
                            + " existing child.");
        }

        handle.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (currentStatus == DRAWER_FILLING) {
                    return true;
                }
                int action = motionEvent.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        originalX = motionEvent.getRawX();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = motionEvent.getRawX() - preMovePosition;
                        preMovePosition = (int) motionEvent.getRawX();
                        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
                        int oldMargin = lp.leftMargin;
                        lp.leftMargin += deltaX;
                        Log.e(TAG, "move distance is " + deltaX);
                        if (lp.leftMargin > 0 || lp.leftMargin < content.getMeasuredWidth() * -1) {
                            lp.leftMargin = oldMargin;
                            return true;
                        }
                        requestLayout();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float endDeltaX = motionEvent.getRawX() - originalX;
                        if (Math.abs(endDeltaX) > content.getMeasuredWidth() / 5 || (int) endDeltaX == 0) {
                            // 如何drawer已经打开，并且继续往右拉 或者已经关闭继续往左拉 这种状态不能触发 toggleDrawer方法。
                            if ((int) endDeltaX == 0 || (endDeltaX > 0 && currentStatus == DRAWER_CLOSED) || (endDeltaX < 0 && currentStatus == DRAWER_OPEN)) {
                                toggleDrawerStatus();
                            }
                        } else {
                            restoreDrawerStatus();
                        }
                        originalX = motionEvent.getRawX();
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleDrawerStatus() {

        final int nextStatus;
        MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();
        int startValue = layoutParams.leftMargin;
        int endValue = 0;

        if (currentStatus == DRAWER_CLOSED) {
            nextStatus = DRAWER_OPEN;
        } else if (currentStatus == DRAWER_OPEN) {
            endValue = this.content.getMeasuredWidth() * -1;
            nextStatus = DRAWER_CLOSED;
        } else {
            nextStatus = DRAWER_CLOSED;
        }
        currentStatus = nextStatus;

        ValueAnimator valueAnimator = ValueAnimator.ofInt(startValue, endValue);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int currentValue = (int) valueAnimator.getAnimatedValue();
                MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
                lp.leftMargin = currentValue;
                if (onDrawerStatusChangeListener != null) {
                    onDrawerStatusChangeListener.OnChange(currentStatus);
                }
                requestLayout();
            }
        });

        valueAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                currentStatus = DRAWER_FILLING;
                if (onDrawerStatusChangeListener != null) {
                    onDrawerStatusChangeListener.OnChange(currentStatus);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                currentStatus = nextStatus;
                if (onDrawerStatusChangeListener != null) {
                    onDrawerStatusChangeListener.OnChange(currentStatus);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });

        float moveDistance = Math.abs(endValue - startValue);
        int durationTime = (int) (300 * (moveDistance / this.content.getMeasuredWidth()));
        valueAnimator.setDuration(durationTime).start();

//        float moveDistance = Math.abs(endValue - startValue);
//        int durationTime = (int)(300 * (moveDistance / this.content.getMeasuredWidth()));
//        new AnimatorAsyncTask(startValue,endValue).execute(durationTime);
    }

    /**
     * 当用户调用的手势移动不够距离时调用这个方法恢复
     */
    private void restoreDrawerStatus() {
        int startValue = 0;
        int endValue = 0;

        MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();
        startValue = layoutParams.leftMargin;
        if (currentStatus == DRAWER_CLOSED) {
            endValue = content.getMeasuredWidth() * -1;
        }

        ValueAnimator valueAnimator = ValueAnimator.ofInt(startValue, endValue);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {

                int currentValue = (int) valueAnimator.getAnimatedValue();
                MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
                lp.leftMargin = currentValue;
                Log.e("Test", "currentValue : " + currentValue);
                requestLayout();
            }
        });

        int durationTime = 500 * (this.content.getMeasuredWidth() - Math.abs(layoutParams.leftMargin)) / this.content.getMeasuredWidth();
        valueAnimator.setDuration(durationTime).start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthResult = 0;
        int heightResult = 0;

        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

        int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);

            int actualWidth = child.getMeasuredWidth();
            int actualHeight = child.getMeasuredHeight();

            // 横向抽屉，宽度最终是累加
            widthResult += actualWidth;

            //高度以最大的值为准
            if (actualHeight > heightResult) {
                heightResult = actualHeight;
            }
        }

        if (widthSpecMode == MeasureSpec.EXACTLY) {
            widthResult = widthSpecSize;
        }

        if (heightSpecMode == MeasureSpec.EXACTLY) {
            heightResult = heightSpecSize;
        }

        setMeasuredDimension(widthResult, heightResult);
    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
        int childCount = getChildCount();
        for (int index = 0; index < childCount; index++) {
            View child = getChildAt(index);
            //MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            //计算childView的left,top,right,bottom
            int lc = left;
            int tc = handle == child ? this.getMeasuredHeight() / 2 : 0;
            int rc = lc + child.getMeasuredWidth();
            int bc = tc + child.getMeasuredHeight();
            Log.e(TAG, child + " , l = " + lc + " , t = " + tc + " , r ="
                    + rc + " , b = " + bc);

            child.layout(lc, tc, rc, bc);

            left += child.getMeasuredWidth();
        }
        left = 0;
    }
    public interface OnDrawerStatusChangeListener {
        void OnChange(int status);
    }

    public void setOnDrawerStatusChangeListener(OnDrawerStatusChangeListener listener) {
        this.onDrawerStatusChangeListener = listener;
    }


    private class AnimatorAsyncTask extends AsyncTask<Integer, Integer, Void> {

        private float startValue;
        private float endValue;

        public AnimatorAsyncTask(int startValue, int endValue) {
            this.startValue = startValue;
            this.endValue = endValue;
        }

        private int getMargin(int progress, int total) {
            int result = 0;
            if (endValue > startValue) {
                progress = total - progress;
                float m = progress;
                result = (int) (m / total * Math.abs(startValue - endValue));
            } else {
                float m = progress;
                result = (int) (m / total * Math.abs(startValue - endValue));
            }
            return result * -1;
        }

        @Override
        protected Void doInBackground(Integer... integers) {
            for (int i = 0; i < integers[0]; i += 10) {
                try {
                    Thread.sleep(10);
                    publishProgress(getMargin(i, integers[0]));
                    currentStatus = DRAWER_FILLING;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            publishProgress(getMargin(integers[0], integers[0]));
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
            lp.leftMargin = values[0];
            requestLayout();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (startValue < endValue) {
                currentStatus = DRAWER_OPEN;
            } else {
                currentStatus = DRAWER_CLOSED;
            }
        }
    }
}
