package com.bfp.alert;

import java.util.List;

public class FirstAidItem {
    public String id;
    public String title;
    public String category;
    public String description;
    public List<String> steps;
    public String videoUrl;
    public String iconEmoji;

    public FirstAidItem() {}

    public FirstAidItem(String id, String title, String category,
                        String description, List<String> steps,
                        String videoUrl, String iconEmoji) {
        this.id          = id;
        this.title       = title;
        this.category    = category;
        this.description = description;
        this.steps       = steps;
        this.videoUrl    = videoUrl;
        this.iconEmoji   = iconEmoji;
    }
}