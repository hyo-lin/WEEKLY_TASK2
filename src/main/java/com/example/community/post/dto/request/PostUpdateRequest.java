package com.example.community.post.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostUpdateRequest {
    @NotBlank
    @Size(max = 26, message = "invalid_title_length")
    @JsonProperty("title")
    private String title;

    @NotBlank
    @JsonProperty("posts_content")
    private String postsContent;


    @JsonProperty("posts_img")
    private String postsImg;
}
