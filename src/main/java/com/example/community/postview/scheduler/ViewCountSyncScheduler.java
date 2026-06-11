package com.example.community.postview.scheduler;

import com.example.community.post.repository.PostRepository;
import com.example.community.postview.buffer.ViewCountBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountSyncScheduler {

    private final ViewCountBuffer viewCountBuffer;
    private final PostRepository postRepository;

    @Scheduled(fixedDelayString = "${scheduler.sync.fixed-delay}")
    @Transactional
    public void sync() {
        if (viewCountBuffer.isEmpty()) return;

        viewCountBuffer.getAll().forEach((postId, count) -> {
            postRepository.findById(postId).ifPresent(post -> {
                for (int i = 0; i < count; i++) {
                    post.increaseViewCount();
                }
            });
            viewCountBuffer.clear(postId);
        });
    }
}