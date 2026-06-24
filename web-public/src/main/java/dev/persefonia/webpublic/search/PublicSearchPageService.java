package dev.persefonia.webpublic.search;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicIndexLimits;
import dev.persefonia.discovery.application.index.PublicSearchIndexQueryService;
import dev.persefonia.discovery.application.index.PublicSearchRequest;
import dev.persefonia.discovery.application.index.PublicSearchResult;
import dev.persefonia.discovery.application.index.PublicSearchResultPage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class PublicSearchPageService {
    static final int PAGE_SIZE = PublicIndexLimits.MAX_SEARCH_LIMIT;
    static final int MAX_PAGE = 500;

    private static final Pattern INTERNAL_WHITESPACE = Pattern.compile("\\s+");
    private static final String QUERY_TOO_SHORT = "Enter at least 2 characters to search.";
    private static final String QUERY_TOO_LONG = "Search query is too long.";
    private static final String QUERY_UNSUPPORTED_CHARACTERS = "Search query contains unsupported characters.";
    private static final String PAGE_INVALID = "Page number is invalid.";

    private final PublicSearchIndexQueryService searchIndex;

    public PublicSearchPageService(PublicSearchIndexQueryService searchIndex) {
        this.searchIndex = Objects.requireNonNull(searchIndex, "searchIndex");
    }

    public PublicSearchPage page(String query, String page, String canonicalUrl, List<String> stylesheetPaths) {
        SearchInput input = normalize(query);
        PageInput pageInput = parsePage(page);
        if (input.error() != null) {
            return validationError(input.normalized(), input.error(), pageInput.page(), canonicalUrl, stylesheetPaths);
        }
        if (query == null || input.normalized().isBlank()) {
            return empty(input.normalized(), validationMessage(pageInput), canonicalUrl, stylesheetPaths);
        }
        if (pageInput.error() != null) {
            return validationError(input.normalized(), pageInput.error(), pageInput.page(), canonicalUrl, stylesheetPaths);
        }

        PublicSearchRequest request =
                new PublicSearchRequest(input.normalized(), PAGE_SIZE, offset(pageInput.page()));
        return from(searchIndex.search(request), pageInput.page(), canonicalUrl, stylesheetPaths);
    }

    private PublicSearchPage empty(
            String normalizedQuery,
            String validationMessage,
            String canonicalUrl,
            List<String> stylesheetPaths) {
        boolean hasValidationError = validationMessage != null;
        return new PublicSearchPage(
                normalizedQuery,
                normalizedQuery,
                false,
                hasValidationError,
                hasValidationError ? validationMessage : "",
                List.of(),
                0L,
                1,
                0,
                false,
                false,
                "",
                "",
                canonicalUrl,
                stylesheetPaths);
    }

    private PublicSearchPage validationError(
            String normalizedQuery,
            String validationMessage,
            int currentPage,
            String canonicalUrl,
            List<String> stylesheetPaths) {
        return new PublicSearchPage(
                normalizedQuery,
                normalizedQuery,
                true,
                true,
                validationMessage,
                List.of(),
                0L,
                currentPage,
                0,
                false,
                false,
                "",
                "",
                canonicalUrl,
                stylesheetPaths);
    }

    private PublicSearchPage from(
            PublicSearchResultPage resultPage,
            int currentPage,
            String canonicalUrl,
            List<String> stylesheetPaths) {
        Objects.requireNonNull(resultPage, "resultPage");
        int totalPages = totalPages(resultPage.totalCount());
        return new PublicSearchPage(
                resultPage.normalizedQuery(),
                resultPage.normalizedQuery(),
                true,
                false,
                "",
                resultPage.results().stream().map(PublicSearchPageService::item).toList(),
                resultPage.totalCount(),
                currentPage,
                totalPages,
                currentPage > 1 && totalPages > 0,
                currentPage < totalPages,
                currentPage > 1 && totalPages > 0 ? pageUrl(resultPage.normalizedQuery(), currentPage - 1) : "",
                currentPage < totalPages ? pageUrl(resultPage.normalizedQuery(), currentPage + 1) : "",
                canonicalUrl,
                stylesheetPaths);
    }

    private static PublicSearchResultItem item(PublicSearchResult result) {
        return new PublicSearchResultItem(
                result.title(),
                result.summary(),
                result.publicUrl(),
                resourceTypeLabel(result.sourceType()),
                languageLabel(result.language()),
                result.publishedAt());
    }

    private static SearchInput normalize(String query) {
        if (query == null) {
            return new SearchInput("", null);
        }
        if (query.chars().anyMatch(PublicSearchPageService::isForbiddenControlCharacter)) {
            return new SearchInput("", QUERY_UNSUPPORTED_CHARACTERS);
        }
        String normalized = INTERNAL_WHITESPACE.matcher(query.trim()).replaceAll(" ");
        if (normalized.isBlank()) {
            return new SearchInput("", null);
        }
        if (normalized.length() < PublicIndexLimits.MIN_SEARCH_QUERY_LENGTH) {
            return new SearchInput(normalized, QUERY_TOO_SHORT);
        }
        if (normalized.length() > PublicIndexLimits.MAX_SEARCH_QUERY_LENGTH) {
            return new SearchInput(normalized, QUERY_TOO_LONG);
        }
        return new SearchInput(normalized, null);
    }

    private static boolean isForbiddenControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint);
    }

    private static PageInput parsePage(String value) {
        if (value == null || value.isBlank()) {
            return new PageInput(1, null);
        }
        try {
            int page = Integer.parseInt(value.trim());
            if (page < 1 || page > MAX_PAGE) {
                return new PageInput(1, PAGE_INVALID);
            }
            return new PageInput(page, null);
        } catch (NumberFormatException exception) {
            return new PageInput(1, PAGE_INVALID);
        }
    }

    private static String validationMessage(PageInput pageInput) {
        return pageInput.error();
    }

    private static int offset(int page) {
        return Math.multiplyExact(page - 1, PAGE_SIZE);
    }

    private static int totalPages(long totalCount) {
        if (totalCount == 0L) {
            return 0;
        }
        long pages = (totalCount + PAGE_SIZE - 1L) / PAGE_SIZE;
        return (int) Math.min(pages, MAX_PAGE);
    }

    private static String pageUrl(String query, int page) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        return "/search?q=" + encodedQuery + "&page=" + page;
    }

    private static String resourceTypeLabel(String sourceType) {
        return switch (sourceType) {
            case "CONTENT_ITEM" -> "Content";
            case "PROJECT" -> "Project";
            default -> sourceType.replace('_', ' ');
        };
    }

    private static String languageLabel(DiscoveryLanguage language) {
        return switch (language) {
            case EN -> "English";
            case TR -> "Turkish";
        };
    }

    private record SearchInput(String normalized, String error) {
    }

    private record PageInput(int page, String error) {
    }
}
