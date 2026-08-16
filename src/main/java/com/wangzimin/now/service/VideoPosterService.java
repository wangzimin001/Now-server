package com.wangzimin.now.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.stereotype.Service;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SystemText;

/**
 * 从手机常见的 H.264 MP4/MOV 视频中提取首帧封面。
 *
 * <p>封面在上传时生成，使时间线只加载轻量 JPEG，不需要为了展示首帧创建
 * 多个原生播放器。输出限制最长边，兼顾列表清晰度和网络体积。</p>
 */
@Service
public class VideoPosterService {

    /**
     * 解码视频第零帧并写入 JPEG 文件。
     *
     * @param videoPath 已完成安全路径校验的视频文件
     * @param posterPath 待写入的封面路径
     * @throws org.springframework.web.server.ResponseStatusException 视频无法解码时抛出友好错误
     */
    public void createFirstFrame(Path videoPath, Path posterPath) {
        try {
            Picture picture = FrameGrab.getFrameFromFile(videoPath.toFile(),
                    BusinessRule.ZERO_COUNT.value());
            if (picture == null) {
                throw ApiErrorCode.VIDEO_PREVIEW_UNAVAILABLE.exception();
            }
            BufferedImage source = AWTUtil.toBufferedImage(picture);
            BufferedImage poster = scaleForTimeline(source);
            Files.createDirectories(posterPath.getParent());
            if (!ImageIO.write(poster, SystemText.JPEG_FORMAT.value(), posterPath.toFile())) {
                throw ApiErrorCode.VIDEO_PREVIEW_UNAVAILABLE.exception();
            }
        } catch (IOException | JCodecException | RuntimeException exception) {
            deletePartialPoster(posterPath);
            if (exception instanceof org.springframework.web.server.ResponseStatusException responseError) {
                throw responseError;
            }
            throw ApiErrorCode.VIDEO_PREVIEW_UNAVAILABLE.exception();
        }
    }

    /**
     * 按整数比例限制封面最长边，避免引入二进制浮点计算。
     *
     * @param source 原始视频首帧
     * @return 原尺寸或缩放后的 RGB 图片
     */
    private BufferedImage scaleForTimeline(BufferedImage source) {
        int maximumEdge = BusinessRule.SOCIAL_VIDEO_POSTER_MAX_EDGE.value();
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= maximumEdge && sourceHeight <= maximumEdge) {
            return source;
        }
        int targetWidth;
        int targetHeight;
        if (sourceWidth >= sourceHeight) {
            targetWidth = maximumEdge;
            targetHeight = Math.max(BusinessRule.COLLECTION_MIN_SIZE.value(),
                    sourceHeight * maximumEdge / sourceWidth);
        } else {
            targetHeight = maximumEdge;
            targetWidth = Math.max(BusinessRule.COLLECTION_MIN_SIZE.value(),
                    sourceWidth * maximumEdge / sourceHeight);
        }
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, BusinessRule.ZERO_COUNT.value(), BusinessRule.ZERO_COUNT.value(),
                    targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /**
     * 删除解码失败时可能留下的不完整封面，不覆盖原始异常。
     *
     * @param posterPath 封面路径
     */
    private void deletePartialPoster(Path posterPath) {
        try {
            Files.deleteIfExists(posterPath);
        } catch (IOException ignored) {
            // 清理失败不应掩盖用户真正需要看到的视频格式错误。
        }
    }
}
