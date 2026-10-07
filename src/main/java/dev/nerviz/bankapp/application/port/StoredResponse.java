package dev.nerviz.bankapp.application.port;

/** What a replay hands back verbatim: status, serialized body, and serialized headers. */
public record StoredResponse(int status, String body, String headers) {}
