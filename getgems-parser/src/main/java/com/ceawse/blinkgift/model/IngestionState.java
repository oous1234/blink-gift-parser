package com.ceawse.blinkgift.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "parser_ingestion_state")
public class IngestionState {

    @Id
    private String id;

    private Long lastProcessedTimestamp;
    private String lastCursor;
    private String status;
}