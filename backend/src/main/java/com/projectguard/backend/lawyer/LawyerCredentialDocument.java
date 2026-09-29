package com.projectguard.backend.lawyer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 변호사 가입 신청 시 제출하는 자격 증명 서류(변호사 자격증 사본 등) 원본.
 * 관리자가 검토를 마칠 때까지 보관해야 하므로, 등기부등본과 달리 즉시 삭제하지 않고
 * DB에 저장해둔다 (개발 단계 인메모리 H2 기준 — 실제 배포 시 오브젝트 스토리지 이전 고려 대상).
 */
@Entity
@Table(name = "lawyer_credential_documents")
public class LawyerCredentialDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private Lawyer lawyer;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    protected LawyerCredentialDocument() {
    }

    public LawyerCredentialDocument(Lawyer lawyer, String fileName, String contentType, byte[] data) {
        this.lawyer = lawyer;
        this.fileName = fileName != null ? fileName : "document";
        this.contentType = contentType != null ? contentType : "application/octet-stream";
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public Lawyer getLawyer() {
        return lawyer;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
