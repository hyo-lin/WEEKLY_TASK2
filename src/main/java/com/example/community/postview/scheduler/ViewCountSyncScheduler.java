package com.example.community.postview.scheduler;

import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.postview.model.PostView;
import com.example.community.postview.repository.PostViewRepository;
import com.example.community.postview.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountSyncScheduler {

    private final ViewCountService viewCountService;
    private final PostRepository postRepository;
    private final PostViewRepository postViewRepository;

    @Scheduled(fixedDelayString = "${scheduler.sync.fixed-delay}")
    @Transactional
    public void syncViewCounts() {
        Set<String> keys = viewCountService.getCountKeys();
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                Long postId = Long.parseLong(key.replace("post:view:", ""));
                long count  = viewCountService.getCachedCount(postId);
                if (count <= 0) continue;

                Post post = postRepository.findById(postId).orElse(null);
                if (post == null) {
                    viewCountService.deleteCountKey(key);
                    continue;
                }

                // PostView 레코드 count개 insert
                List<PostView> views = LongStream.range(0, count)
                        .mapToObj(i -> new PostView(post))
                        .collect(Collectors.toList());
                postViewRepository.saveAll(views);

                viewCountService.deleteCountKey(key);
                log.info("동기화 완료 postId={} count={}", postId, count);

            } catch (Exception e) {
                log.error("동기화 실패 key={}", key, e);
            }
        }
    }
}
