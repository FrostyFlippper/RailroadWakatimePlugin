package org.FrostyFlippper;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class Heartbeat {
    private final String entity;
    private final Integer lineCount;
    private final Integer lineNumber;
    private final Integer cursorPosition;
    private final BigDecimal timestamp;
    private final Boolean isWrite;
    private final Boolean isUnsavedFile;
    private final String project;
    private final String language;
    private final Boolean isBuilding;

    private Heartbeat(Builder builder){
        this.entity = builder.entity;
        this.lineCount = builder.lineCount;
        this.lineNumber = builder.lineNumber;
        this.cursorPosition = builder.cursorPosition;
        this.timestamp = builder.timestamp;
        this.isWrite = builder.isWrite;
        this.isUnsavedFile = builder.isUnsavedFile;
        this.project = builder.project;
        this.language = builder.language;
        this.isBuilding = builder.isBuilding;
    }

    public static class Builder {
        private String entity;
        private Integer lineCount;
        private Integer lineNumber;
        private Integer cursorPosition;
        private BigDecimal timestamp;
        private Boolean isWrite;
        private Boolean isUnsavedFile;
        private String project;
        private String language;
        private Boolean isBuilding;

        public Builder setEntity(String entity) {
            this.entity = entity;
            return this;
        }

        public Builder setLineCount(Integer lineCount) {
            this.lineCount = lineCount;
            return this;
        }

        public Builder setLineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder setCursorPosition(Integer cursorPosition) {
            this.cursorPosition = cursorPosition;
            return this;
        }

        public Builder setTimestamp(BigDecimal timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder setWrite(Boolean write) {
            isWrite = write;
            return this;
        }

        public Builder setUnsavedFile(Boolean unsavedFile) {
            isUnsavedFile = unsavedFile;
            return this;
        }

        public Builder setProject(String project) {
            this.project = project;
            return this;
        }

        public Builder setLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder setBuilding(Boolean building) {
            isBuilding = building;
            return this;
        }

        public Heartbeat build(){
            return new Heartbeat(this);
        }
    }
}