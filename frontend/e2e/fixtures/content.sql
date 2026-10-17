-- Destructive browser fixture: browserCheck applies this to disposable PostgreSQL.
-- It may also be loaded explicitly into disposable local Compose data for visual review.
-- The active CV row references a PDF created separately by browserCheck; local Compose
-- does not create that file, so the fixture alone cannot qualify CV download.
TRUNCATE discovery.discoverable_resources, publishing.content_items,
    portfolio.projects, portfolio.personal_profiles, portfolio.active_cv_documents, media.assets CASCADE;

UPDATE portfolio.site_presentation_settings
SET default_language = 'EN', promote_featured_projects = true,
    promote_latest_writing = true, promote_research_highlights = true,
    featured_project_limit = 3, latest_writing_limit = 3, research_highlight_limit = 3,
    standard_public_theme = 'SYSTEM'
WHERE singleton_key = true;

INSERT INTO portfolio.personal_profiles (id, display_name, active, created_at, updated_at, version)
VALUES ('10000000-0000-0000-0000-000000000001', 'Fixture Engineer', true, now(), now(), 0);
INSERT INTO portfolio.profile_localizations (id, profile_id, language, short_bio, long_bio, location_text)
VALUES
('11000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'EN',
 'A software engineer exploring reliable systems and clear explanations.', 'Test-only profile biography.', 'Istanbul'),
('11000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'TR',
 'Güvenilir sistemler ve anlaşılır açıklamalar üzerine çalışan bir yazılım mühendisi.', 'Yalnızca test için profil metni.', 'İstanbul');
INSERT INTO portfolio.current_focus_items (id, profile_localization_id, text, sort_order)
SELECT md5('focus-en-' || i)::uuid, '11000000-0000-0000-0000-000000000001',
       (ARRAY['Reliable publishing workflows', 'Accessible editorial interfaces', 'Practical architecture decisions'])[i], i
FROM generate_series(1, 3) AS i;
INSERT INTO portfolio.current_focus_items (id, profile_localization_id, text, sort_order)
SELECT md5('focus-tr-' || i)::uuid, '11000000-0000-0000-0000-000000000002',
       (ARRAY['Güvenilir yayın akışları', 'Erişilebilir editoryal arayüzler', 'Uygulanabilir mimari kararlar'])[i], i
FROM generate_series(1, 3) AS i;
INSERT INTO portfolio.external_profile_links (id, profile_id, label, url, sort_order)
SELECT md5('profile-link-' || i)::uuid, '10000000-0000-0000-0000-000000000001',
       (ARRAY['Code', 'Writing', 'Research'])[i],
       'https://example.test/' || (ARRAY['code', 'writing', 'research'])[i], i
FROM generate_series(1, 3) AS i;

INSERT INTO portfolio.projects (id, status, visibility, featured, sort_order, created_at, updated_at, version)
SELECT md5('browser-project-' || i)::uuid, 'ACTIVE', 'PUBLIC', true, i,
       timestamp with time zone '2026-06-01 00:00:00+00', timestamp with time zone '2026-06-15 00:00:00+00', 0
FROM generate_series(1, 3) AS i;
INSERT INTO portfolio.project_localizations (id, project_id, language, slug, title, summary)
SELECT md5('browser-project-en-' || i)::uuid, md5('browser-project-' || i)::uuid, 'EN',
       'fixture-project-' || i,
       (ARRAY['A publishing system for resilient editorial workflows',
              'Making complex operational decisions visible through a long project title that stresses responsive wrapping',
              'A compact research archive'])[i],
       (ARRAY['An exploration of public publishing, review, and durable content state.',
              'A detailed case study about boundaries, observability, and the tradeoffs of a modular application.',
              'A small system for organising experiments and documenting what changed.'])[i]
FROM generate_series(1, 3) AS i;
INSERT INTO portfolio.project_localizations (id, project_id, language, slug, title, summary)
SELECT md5('browser-project-tr-' || i)::uuid, md5('browser-project-' || i)::uuid, 'TR',
       'ornek-proje-' || i,
       (ARRAY['Dayanıklı editoryal süreçler için bir yayın sistemi',
              'Karmaşık operasyonel kararları görünür kılan ve mobil satır kaymasını sınayan uzun bir proje başlığı',
              'Küçük bir araştırma arşivi'])[i],
       (ARRAY['Açık yayıncılık, inceleme ve kalıcı içerik durumu üzerine bir çalışma.',
              'Sınırlar, gözlemlenebilirlik ve modüler uygulama tercihleri üzerine ayrıntılı bir örnek.',
              'Deneyleri düzenleyen ve değişiklikleri belgeleyen küçük bir sistem.'])[i]
FROM generate_series(1, 3) AS i;
INSERT INTO portfolio.project_technologies (id, project_id, name, normalized_name, category, sort_order)
SELECT md5('browser-tech-' || i || '-' || j)::uuid, md5('browser-project-' || i)::uuid,
       (ARRAY['Java', 'PostgreSQL', 'TypeScript'])[j],
       lower((ARRAY['Java', 'PostgreSQL', 'TypeScript'])[j]),
       (ARRAY['LANGUAGE', 'DATABASE', 'LANGUAGE'])[j], j
FROM generate_series(1, 3) AS i CROSS JOIN generate_series(1, 3) AS j;

INSERT INTO publishing.content_items
    (id, type, status, visibility, language, slug, title, summary, markdown_source,
     canonical_path, published_at, created_at, updated_at, version)
SELECT md5('browser-content-' || lang || '-' || kind || '-' || i)::uuid,
       CASE WHEN kind = 'writing' AND i = 3 THEN 'NOTE' WHEN kind = 'writing' THEN 'ARTICLE' ELSE 'RESEARCH' END,
       'PUBLISHED', 'PUBLIC', lang,
       CASE WHEN kind = 'writing' THEN 'fixture-writing-' || i ELSE 'fixture-research-' || i END,
       CASE WHEN lang = 'EN' THEN
         CASE WHEN kind = 'writing' THEN (ARRAY[
           'How a small publishing system keeps public state explicit',
           'A deliberately long writing title about architecture decisions and the cost of hidden coupling',
           'Notes on reliable browser verification'])[i]
         ELSE (ARRAY[
           'Researching the limits of content discovery',
           'An experiment in responsive archive geometry with a deliberately long title',
           'Observations on measured runtime motion'])[i] END
       ELSE CASE WHEN kind = 'writing' THEN (ARRAY[
           'Küçük bir yayın sistemi açık durumu nasıl korur?',
           'Mimari kararlar ve gizli bağımlılıkların maliyeti üzerine uzun bir yazı başlığı',
           'Güvenilir tarayıcı doğrulaması üzerine notlar'])[i]
         ELSE (ARRAY[
           'İçerik keşfinin sınırları üzerine araştırma',
           'Duyarlı arşiv geometrisi üzerine uzun başlıklı bir deney',
           'Ölçülen hareket performansına ilişkin gözlemler'])[i] END END,
       CASE WHEN lang = 'EN' THEN 'A test-only summary with enough detail to exercise the editorial layout.'
            ELSE 'Editoryal düzeni sınamak için yeterli ayrıntı içeren, yalnızca teste özel bir özet.' END,
       '# Fixture content',
       '/' || lower(lang) || '/' || CASE WHEN kind = 'research' THEN 'research' WHEN i = 3 THEN 'notes' ELSE 'articles' END
           || '/' || CASE WHEN kind = 'writing' THEN 'fixture-writing-' || i ELSE 'fixture-research-' || i END,
       timestamp with time zone '2026-06-20 00:00:00+00' + i * interval '1 day',
       timestamp with time zone '2026-06-01 00:00:00+00',
       timestamp with time zone '2026-06-20 00:00:00+00' + i * interval '1 day', 0
FROM (VALUES ('EN'), ('TR')) AS languages(lang)
CROSS JOIN (VALUES ('writing'), ('research')) AS kinds(kind)
CROSS JOIN generate_series(1, 3) AS i;
INSERT INTO publishing.content_render_snapshots
    (content_item_id, rendered_html, rendered_at, renderer_version, reading_time_minutes, contains_mermaid)
SELECT id, '<p>This is test-only published content.</p>', now(), 'browser-fixture', 1, false
FROM publishing.content_items WHERE slug LIKE 'fixture-%';
UPDATE publishing.content_render_snapshots snapshots
SET rendered_html = '<h2 id="fixture-section">Fixture section</h2><p>This is test-only published content.</p>'
FROM publishing.content_items items
WHERE snapshots.content_item_id = items.id AND items.slug = 'fixture-writing-1';
INSERT INTO publishing.content_rendered_headings (id, content_item_id, level, text, anchor, position)
SELECT md5('browser-heading-' || id::text)::uuid, id, 2, 'Fixture section', 'fixture-section', 1
FROM publishing.content_items WHERE slug = 'fixture-writing-1';
INSERT INTO discovery.discoverable_resources
    (id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
     public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
     sitemap_eligibility, feed_eligibility, published_at, source_updated_at, search_text, created_at, version)
SELECT md5('browser-project-discovery-' || localizations.id::text)::uuid,
       'PROFILE_PORTFOLIO', 'PROJECT', projects.id, 'PROJECT', 'DETAIL', localizations.language,
       '/' || lower(localizations.language) || '/projects/' || localizations.slug,
       'https://example.test/' || lower(localizations.language) || '/projects/' || localizations.slug,
       localizations.title, localizations.summary, 'INDEX', 'ELIGIBLE', 'ELIGIBLE', 'NOT_ELIGIBLE',
       NULL, projects.updated_at, localizations.title || ' ' || localizations.summary, projects.created_at, 0
FROM portfolio.projects projects
JOIN portfolio.project_localizations localizations ON localizations.project_id = projects.id;
INSERT INTO discovery.discoverable_resources
    (id, source_context, source_type, source_entity_id, resource_type, route_purpose, language,
     public_url, canonical_url, title, summary, indexing_policy, search_eligibility,
     sitemap_eligibility, feed_eligibility, published_at, source_updated_at, search_text, created_at, version)
SELECT md5('browser-discovery-' || id::text)::uuid,
       'CONTENT_PUBLISHING', 'CONTENT_ITEM', id, type, 'DETAIL', language,
       canonical_path, 'https://example.test' || canonical_path, title, summary,
       'INDEX', 'ELIGIBLE', 'ELIGIBLE', 'ELIGIBLE', published_at, updated_at,
       title || ' ' || summary, created_at, 0
FROM publishing.content_items WHERE slug LIKE 'fixture-%';

INSERT INTO media.assets
    (id, original_filename, stored_filename, storage_path, content_type, file_extension,
     size_bytes, checksum, kind, visibility, processing_status, created_at, updated_at, version)
VALUES ('10000000-0000-0000-0000-0000000000c1', 'browser-cv.pdf', 'browser-cv.pdf',
        'original/browser-cv.pdf', 'application/pdf', 'pdf', 51, 'browser-cv-fixture',
        'PDF', 'PUBLIC', 'NOT_REQUIRED', now(), now(), 0);
INSERT INTO portfolio.active_cv_documents
    (id, active_cv_profile_id, language, asset_id, display_label, selected_at, created_at, updated_at)
SELECT md5('browser-cv-' || language)::uuid, '00000000-0000-0000-0000-000000000801', language,
       '10000000-0000-0000-0000-0000000000c1', 'Fixture CV', now(), now(), now()
FROM (VALUES ('EN'), ('TR')) AS languages(language);

-- Gallery cover uses the supplied hero derivative as test-only author-selected media.
-- browserCheck creates these files in its disposable storage root.
INSERT INTO media.assets
    (id, original_filename, stored_filename, storage_path, content_type, file_extension,
     size_bytes, checksum, kind, visibility, image_width, image_height, decorative,
     processing_status, created_at, updated_at, version)
VALUES ('10000000-0000-0000-0000-0000000000c2', 'browser-cover.webp', 'browser-cover.webp',
        'original/browser-cover.webp', 'image/webp', 'webp', 299892,
        'eaa782e7a0ccf61b45137480fcabbb50493bbdf512f3b21d3d05785970f2874c',
        'IMAGE', 'PUBLIC', 1920, 1072, true, 'PROCESSED', now(), now(), 0);
INSERT INTO media.asset_variants
    (id, asset_id, name, width, height, content_type, size_bytes, storage_path, checksum, created_at)
VALUES ('10000000-0000-0000-0000-0000000000c3', '10000000-0000-0000-0000-0000000000c2',
        'medium', 1920, 1072, 'image/webp', 299892, 'variants/browser-cover.webp',
        'eaa782e7a0ccf61b45137480fcabbb50493bbdf512f3b21d3d05785970f2874c', now());
UPDATE portfolio.projects SET cover_asset_id='10000000-0000-0000-0000-0000000000c2'
WHERE id=md5('browser-project-1')::uuid;
UPDATE publishing.content_items SET og_image_asset_id='10000000-0000-0000-0000-0000000000c2'
WHERE slug='fixture-writing-3';
