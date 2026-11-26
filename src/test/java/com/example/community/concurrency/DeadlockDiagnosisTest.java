package com.example.community.concurrency;

import com.example.community.postlike.service.PostLikeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/** 좋아요 동시 실행 직후 InnoDB 의 LATEST DETECTED DEADLOCK 구간을 build/deadlock-status.txt 로 저장한다. */
@Tag("benchmark")
class DeadlockDiagnosisTest extends ConcurrencyTestSupport {

    @Autowired PostLikeService postLikeService;

    @Test
    void captureDeadlock() throws Exception {
        List<Long> users = createUsers(101);
        Long postId = createPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Long uid = users.get(i);
            tasks.add(() -> { postLikeService.like(postId, uid); return null; });
        }
        System.out.println("[DIAG] " + runConcurrently(tasks));
        String status = (String) jdbc.queryForMap("SHOW ENGINE INNODB STATUS").get("Status");
        int from = status.indexOf("LATEST DETECTED DEADLOCK");
        int to = status.indexOf("------------\nTRANSACTIONS", from);
        String section = from >= 0 ? status.substring(from, to > from ? to : Math.min(status.length(), from + 8000)) : "(no deadlock section)";
        Files.writeString(Path.of("build", "deadlock-status.txt"), section);
        System.out.println("[DIAG] saved " + section.length() + " chars");
    }
}
