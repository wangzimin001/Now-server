package com.wangzimin.now.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SocialSchemaTest {

    /** 验证基础社交迁移包含好友、会话、动态及互动表。 */
    @Test
    void socialMigrationContainsFriendConversationMomentAndInteractionTables() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V27__add_social_system.sql"));

        assertTrue(migration.contains("ADD COLUMN public_id"));
        assertTrue(migration.contains("CREATE TABLE friend_request"));
        assertTrue(migration.contains("CREATE TABLE friendship"));
        assertTrue(migration.contains("CREATE TABLE social_conversation"));
        assertTrue(migration.contains("CREATE TABLE social_message"));
        assertTrue(migration.contains("CREATE TABLE social_post"));
        assertTrue(migration.contains("CREATE TABLE social_post_like"));
        assertTrue(migration.contains("CREATE TABLE social_post_comment"));
    }

    /** 验证演示媒体迁移会绑定六个头像、动态图片及时间索引。 */
    @Test
    void socialDemoMediaMigrationContainsLocalAssetsAndChronologicalIndex() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V29__add_social_demo_media.sql"));

        assertTrue(migration.contains("/demo-media/avatars/annie.png"));
        assertTrue(migration.contains("/demo-media/avatars/fiona.png"));
        assertTrue(migration.contains("INSERT INTO social_attachment"));
        assertTrue(migration.contains("INSERT IGNORE INTO social_post_attachment"));
        assertTrue(migration.contains("idx_social_post_feed (deleted_at, created_at, id)"));
    }

    /** 验证布局演示迁移和本地资源完整覆盖一至九图动态。 */
    @Test
    void socialGridDemoProvidesOneThroughNineImageLayouts() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V30__add_social_grid_layout_demos.sql"));

        for (int imageCount = 1; imageCount <= 9; imageCount++) {
            String imageName = String.format("layout-%02d.jpg", imageCount);
            assertTrue(migration.contains(imageCount + " 图"));
            assertTrue(Files.exists(Path.of(
                    "src/main/resources/static/demo-media/moments/grid-layout", imageName)));
        }
        assertTrue(migration.contains("image_index.sequence_number <= demo_post.image_count"));
    }

    /** 验证视频迁移锁定媒体类型并把九宫格预览恢复为完整原图。 */
    @Test
    void socialVideoMigrationConstrainsTypesAndRestoresFullPreviewImages() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V31__add_social_video_media.sql"));

        assertTrue(migration.contains("attachment_type IN ('IMAGE', 'VIDEO', 'FILE')"));
        assertTrue(migration.contains("'TEXT', 'EMOJI', 'IMAGE', 'VIDEO', 'FILE', 'SYSTEM'"));
        assertTrue(migration.contains("/demo-media/moments/annie-bench-press.jpg"));
        assertTrue(migration.contains("WHERE stored_name LIKE 'demo-grid-%'"));
    }

    /** 验证首帧地址、互动通知及未读索引随 V32 一并迁移。 */
    @Test
    void socialNotificationMigrationAddsPosterAndUnreadNotificationStorage() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V32__add_social_video_posters_and_notifications.sql"));

        assertTrue(migration.contains("ADD COLUMN poster_url"));
        assertTrue(migration.contains("CREATE TABLE social_notification"));
        assertTrue(migration.contains("notification_type IN ('POST_LIKE', 'POST_COMMENT')"));
        assertTrue(migration.contains("recipient_user_id <> actor_user_id"));
        assertTrue(migration.contains("idx_social_notification_recipient_unread"));
    }

    /** 验证本机互动演示数据同时生成可见点赞、评论和 Jimmy 的未读通知。 */
    @Test
    void unreadMomentDemoContainsVisibleInteractionsAndNotifications() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V34__seed_unread_moment_interactions.sql"));

        assertTrue(migration.contains("INSERT IGNORE INTO social_post_like"));
        assertTrue(migration.contains("INSERT INTO social_post_comment"));
        assertTrue(migration.contains("'POST_LIKE'"));
        assertTrue(migration.contains("'POST_COMMENT'"));
        assertTrue(migration.contains("read_at = NULL"));
    }

    /** 验证朋友圈查询使用发布时间倒序，且分页沿用同一复合顺序。 */
    @Test
    void socialMomentFeedUsesReverseChronologicalCursor() throws IOException {
        String repository = Files.readString(Path.of(
                "src/main/java/com/wangzimin/now/repository/SocialMomentRepository.java"));

        assertTrue(repository.contains("(p.created_at, p.id) <"));
        assertTrue(repository.contains("FROM social_post cursor_post"));
        assertTrue(repository.contains("ORDER BY p.created_at DESC, p.id DESC"));
    }
}
