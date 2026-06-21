package com.example.community.post.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostCursorResponse {
    private List<PostResponse> posts;
    private boolean hasNext;
}
