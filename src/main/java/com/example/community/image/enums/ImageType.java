package com.example.community.image.enums;

public enum ImageType {
    POST,
    PROFILE;

    public String toFolder() {
        return this.name().toLowerCase();
    }
}
