package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.jcodec.api.awt.AWTSequenceEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoPosterServiceTest {

    @TempDir
    Path tempDirectory;

    /** 验证真实 H.264 MP4 的第一帧会被提取成可读取 JPEG。 */
    @Test
    void createsReadablePosterFromFirstH264Frame() throws IOException {
        Path video = tempDirectory.resolve("training.mp4");
        Path poster = tempDirectory.resolve("training.poster.jpg");
        BufferedImage frame = createFrame();
        AWTSequenceEncoder encoder = AWTSequenceEncoder.create24Fps(video.toFile());
        encoder.encodeImage(frame);
        encoder.finish();

        new VideoPosterService().createFirstFrame(video, poster);

        assertTrue(Files.size(poster) > 0L);
        BufferedImage decoded = ImageIO.read(poster.toFile());
        assertNotNull(decoded);
        assertEquals(frame.getWidth(), decoded.getWidth());
        assertEquals(frame.getHeight(), decoded.getHeight());
    }

    /**
     * 创建具有稳定色块的偶数尺寸视频帧，满足 H.264 色度采样要求。
     *
     * @return 320 乘 180 的 RGB 图片
     */
    private BufferedImage createFrame() {
        BufferedImage frame = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            graphics.setColor(new Color(24, 30, 27));
            graphics.fillRect(0, 0, frame.getWidth(), frame.getHeight());
            graphics.setColor(new Color(196, 250, 59));
            graphics.fillRect(80, 45, 160, 90);
        } finally {
            graphics.dispose();
        }
        return frame;
    }
}
