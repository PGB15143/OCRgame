package com.example.projectocr;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;

public class WordAdapter extends ArrayAdapter<String> {

    private Context context;
    public ArrayList<String> fileList;
    private boolean[] itemChecked;

    public WordAdapter(Context context, ArrayList<String> fileList) {
        super(context, 0, fileList);
        this.context = context;
        this.fileList = fileList;
        this.itemChecked = new boolean[fileList.size()];
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
        }

        CheckBox checkBox = convertView.findViewById(R.id.checkBox);
        TextView textView = convertView.findViewById(R.id.textView);

        textView.setText(fileList.get(position));
        checkBox.setChecked(itemChecked[position]);

        // 체크박스 클릭만 따로 처리 (선택 체크/해제)
        checkBox.setOnClickListener(v -> {
            itemChecked[position] = checkBox.isChecked();
        });

        return convertView;
    }


    public ArrayList<String> getSelectedFiles() {
        ArrayList<String> selectedFiles = new ArrayList<>();
        for (int i = 0; i < itemChecked.length; i++) {
            if (itemChecked[i]) {
                selectedFiles.add(fileList.get(i));
            }
        }
        return selectedFiles;
    }
}
