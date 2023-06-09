package com.spring.blog.service.impl;

import com.spring.blog.entity.Attachment;
import com.spring.blog.entity.Post;
import com.spring.blog.entity.common.LocalDate;
import com.spring.blog.exception.FileStorageException;
import com.spring.blog.exception.MyFileNotFoundException;
import com.spring.blog.payload.request.AttachmentRequestDto;
import com.spring.blog.repository.AttachmentRepository;
import com.spring.blog.repository.PostRepository;
import com.spring.blog.security.UserPrincipal;
import com.spring.blog.service.AttachmentService;
import com.spring.blog.utils.AttachmentUtil;
import com.spring.blog.utils.Md5Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final Path fileStorageLocation;
    private final AttachmentRepository attachmentRepository;
    private final PostRepository postRepository;

    @Autowired
    public AttachmentServiceImpl(AttachmentUtil attachmentUtil, AttachmentRepository attachmentRepository, PostRepository postRepository) {
        String uploadDir = attachmentUtil.getUploadDir();
        if (uploadDir == null) {
            throw new IllegalArgumentException("Upload directory path is null. Check AttachmentUtil configuration.");
        }

        this.fileStorageLocation = Paths.get(attachmentUtil.getUploadDir()).toAbsolutePath().normalize();
        this.attachmentRepository = attachmentRepository;
        this.postRepository = postRepository;

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        try {
            if (fileName.contains("..")) {
                throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
            }

            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return fileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String fileName) throws FileNotFoundException {
        try {
            Path file = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(file.toUri());

            if(resource.exists() || resource.isReadable()) {
                return resource;
            }else {
                throw new FileNotFoundException("Could not find file");
            }
        } catch (MalformedURLException e) {
            throw new FileNotFoundException("Could not download file");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    @Transactional
    public Attachment findByAttachment(Long id) {
        return attachmentRepository.findById(id).get();
    }

    @Override
    public Attachment uploadAttachment(MultipartFile file, Long postId, UserPrincipal currentUser) {
        try {
            String origFilename = file.getOriginalFilename();
            String filename = new Md5Util(origFilename).toString();

            String savePath = System.getProperty("user.dir") + File.separator + "files";
            File saveFolder = new File(savePath);

            if (!saveFolder.exists()) {
                if (!saveFolder.mkdir()) {
                    throw new RuntimeException("Failed to create directory: " + savePath);
                }
            }

            String filePath = savePath + File.separator + filename;
            file.transferTo(new File(filePath));

            Optional<Post> optionalPost = postRepository.findById(postId);
            Post post = optionalPost.orElseThrow(() -> new IllegalArgumentException("Post not found"));

            Attachment attachment = Attachment.builder()
                    .originFileName(origFilename)
                    .fileName(filename)
                    .fileDownloadUri(savePath)
                    .fileType(file.getContentType())
                    .size(file.getSize())
                    .postId(post.getId())
                    .userId(currentUser.getId())
                    .date(LocalDate.builder()
                            .createdAt(LocalDateTime.now())
                            .build())
                    .build();

            return attachmentRepository.save(attachment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}