package com.immersionrc.LapRFTiming;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by nab on 21/06/17.
 */

// So WTF does this class do?. Related to the fixed first two columns of the table?

public class DetectionTableViewItemDecoration extends RecyclerView.ItemDecoration
{
    public final int numSlots = lapRFConstants.numPilotSlots;
    public DetectionTableViewItemDecoration()
    {

    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);

        if (parent.getChildAdapterPosition(view) == 0) {
            // header
            return;
        }
        else
        {
            outRect.top = 0;
            outRect.bottom = 0;
            outRect.left = 5;
            outRect.right = 5;
        }
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state)
    {
        // this stuff appears to be responsible for the 'freezing' of the first two columns.
        // remove it for now while we clean up the table
        //

        //super.onDrawOver(c, parent, state);

        // get header view from parent
//        header = parent.getChildAt(0);
        /*
        RecyclerView.ViewHolder vh1 = parent.findViewHolderForAdapterPosition(0);
        if (vh1 == null)
            return;

        View header1 = null;
        header1 =  vh1.itemView ;
        if (header1 == null)
            return;

        fixLayoutSize(parent, header1);

        RecyclerView.ViewHolder vh2 = parent.findViewHolderForAdapterPosition(1);
        if (vh2 == null)
            return;

        View header2 = null;
        header2 =  vh2.itemView ;
        if (header2 == null)
            return;

        fixLayoutSize(parent, header2);

        c.save();
        c.translate(0, 0);
        header1.draw(c);
        c.translate(header1.getWidth()+5, 0);
        header2.draw(c);
        c.restore();
        */

    }

    private void fixLayoutSize(ViewGroup parent, View view)
    {
        // Specs for parent (RecyclerView)
        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

        // Specs for children (headers)
        int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, parent.getPaddingLeft() + parent.getPaddingRight(), view.getLayoutParams().width);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, parent.getPaddingTop() + parent.getPaddingBottom(), view.getLayoutParams().height);

        view.measure(childWidthSpec, childHeightSpec);

        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
}
