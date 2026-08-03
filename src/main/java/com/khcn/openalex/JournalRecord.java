package com.khcn.openalex;

public record JournalRecord(
    Integer sourceId,
    String title,
    String issnClean,
    Double sjrScore,
    String bestQuartile,
    Integer hIndex,
    Integer rankingYear
) {}
