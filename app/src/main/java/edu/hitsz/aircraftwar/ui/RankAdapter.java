package edu.hitsz.aircraftwar.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import edu.hitsz.aircraftwar.R;
import edu.hitsz.aircraftwar.db.ScoreRecord;

/**
 * 排行榜列表适配器（对应课件第 7–9 页）。
 * <p>
 * 为什么选 {@link BaseAdapter} 而不是 ArrayAdapter / SimpleAdapter：
 * 排行榜每行要放「排名 / 姓名 / 分数 / 删除按钮」，还要在行内绑删除事件。
 * 课件第 7 页的对比表里，只有 BaseAdapter 能在内部直接给控件绑交互事件
 * （ArrayAdapter 做不到，SimpleAdapter 要绕到列表外部去找控件、耦合度高）。
 * <p>
 * 核心是 {@link #getView} 里的「复用 convertView + ViewHolder」：
 * 列表滑动时同一批行视图会被反复使用，把 findViewById 的结果缓存在 ViewHolder 里、
 * 存到 convertView 的 tag 上，复用时直接取，避免每次都重新查找控件。
 */
public class RankAdapter extends BaseAdapter {

    /** 点删除按钮的回调，由 Activity 实现（删库 + 刷新） */
    public interface OnDeleteListener {
        void onDelete(ScoreRecord record);
    }

    private final LayoutInflater inflater;
    private final List<ScoreRecord> data = new ArrayList<>();
    private final OnDeleteListener deleteListener;

    public RankAdapter(Context context, OnDeleteListener deleteListener) {
        this.inflater = LayoutInflater.from(context);
        this.deleteListener = deleteListener;
    }

    /** 替换数据源并刷新列表（课件第 4 页：数据随列表刷新） */
    public void submit(List<ScoreRecord> records) {
        data.clear();
        data.addAll(records);
        // 通知 ListView 数据变了，要求重绘（课件第 4 页点名的方法）
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public ScoreRecord getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            // ① 首次：把 XML 单行布局解析成一个 View，并缓存里面的控件
            convertView = inflater.inflate(R.layout.item_rank, parent, false);
            holder = new ViewHolder();
            holder.tvRank = convertView.findViewById(R.id.tv_rank);
            holder.tvName = convertView.findViewById(R.id.tv_name);
            holder.tvScore = convertView.findViewById(R.id.tv_score);
            holder.btnDelete = convertView.findViewById(R.id.btn_delete);
            convertView.setTag(holder);
        } else {
            // ② 复用：直接取出缓存的控件，不再 findViewById
            holder = (ViewHolder) convertView.getTag();
        }

        // ③ 绑定数据
        ScoreRecord record = data.get(position);
        int rank = position + 1;
        holder.tvRank.setText(String.valueOf(rank));
        holder.tvName.setText(record.name);
        holder.tvScore.setText(String.valueOf(record.score));

        // 第一名高亮（课件第 4 页明确要求）
        boolean isFirst = position == 0;
        convertView.setBackgroundResource(
                isFirst ? R.drawable.bg_rank_item_first : R.drawable.bg_rank_item);
        holder.tvRank.setTextColor(parent.getContext().getColor(
                isFirst ? R.color.accent : R.color.text_muted));

        // ④ 绑定控件事件（注意：复用时必须重新绑，否则会删掉上一行对应的记录）
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(record);
            }
        });

        return convertView;
    }

    /** 行内控件的缓存（课件第 9 页提到的「内部类，缓存行控件，提高性能」） */
    private static class ViewHolder {
        TextView tvRank;
        TextView tvName;
        TextView tvScore;
        Button btnDelete;
    }
}
