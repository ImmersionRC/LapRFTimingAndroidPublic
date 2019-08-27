// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by urssc on 11-Apr-17.
 */

public class FrequenciesAdapter extends RecyclerView.Adapter<FrequenciesAdapter.ViewHolder> {
    private ArrayList<FrequencyItem> mFrequencies;

    /***** Creating OnItemClickListener *****/

    // Define listener member variable
    private OnItemClickListener listener;
    // Define the listener interface
    public interface OnItemClickListener {
        void onItemClick(View itemView, int position);
    }

    // Define the method that allows the parent activity or fragment to define the listener
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView mChannelTextView;
        public TextView mFrequencyTextView;
        public LinearLayout mLayout;
        public Context mContext;
        public ViewHolder(final View v) {
            super(v);
            mChannelTextView = (TextView)v.findViewById(R.id.channelTextView);
            mFrequencyTextView = (TextView)v.findViewById(R.id.frequencyTextView);
            mLayout = (LinearLayout)v.findViewById(R.id.frequencyItemLayout);
            mContext = mChannelTextView.getContext();

            // Setup the click listener
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Triggers click upwards to the adapter on click
                    if (listener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onItemClick(itemView, position);
                        }
                    }
                }
            });
        }


    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public FrequenciesAdapter(ArrayList<FrequencyItem> freqs) {
        mFrequencies = freqs;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public FrequenciesAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                            int viewType) {
        // create a new view
        View v = (View) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.channel_layout, parent, false);
        // set the view's size, margins, paddings and layout parameters

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        //holder.mTextView.setText(mFrequencies[position]);
        int freq = mFrequencies.get(position).frequency;
        holder.mChannelTextView.setText(Integer.toString(position + 1));
        holder.mFrequencyTextView.setText(Integer.toString(freq));
        if (mFrequencies.get(position).selected)
            holder.mLayout.setBackgroundColor(holder.mContext.getResources().getColor(R.color.freq_adapter_selected));
        else
            holder.mLayout.setBackgroundColor(holder.mContext.getResources().getColor(R.color.freq_adapter_unselected));
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mFrequencies.size();
    }
}
