package com.juanchiz.fishfeeder.ui.schedule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.model.ScheduleItem;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    private final List<ScheduleItem> items;
    private final OnRemoveListener listener;

    public ScheduleAdapter(List<ScheduleItem> items, OnRemoveListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScheduleItem item = items.get(position);
        holder.tvHora.setText(item.horaFormateada());
        String porcionesLabel = holder.itemView.getContext()
                .getString(R.string.portions) + ": " + item.porciones;
        holder.tvPorciones.setText(porcionesLabel);
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(holder.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvHora;
        TextView tvPorciones;
        ImageButton btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            tvHora = itemView.findViewById(R.id.tvHora);
            tvPorciones = itemView.findViewById(R.id.tvPorciones);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
