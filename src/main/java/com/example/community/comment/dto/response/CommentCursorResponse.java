package com.example.community.comment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CommentCursorResponse {
    private List<CommentResponse> comments;
    private boolean hasNext;
}
