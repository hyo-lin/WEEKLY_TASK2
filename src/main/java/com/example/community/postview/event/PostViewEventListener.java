package com.example.community.postview.event;

import com.example.community.postview.buffer.ViewCountBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostViewEventListener {

    private final ViewCountBuffer viewCountBuffer;

    @EventListener
    public void handle(PostViewedEvent event) {
        viewCountBuffer.increment(event.postId());
    }
}