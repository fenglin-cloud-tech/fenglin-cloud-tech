package com.fenglin.search.web.dto;

public record SearchRecord(String docId, String title, String highlight, double score) {
}
