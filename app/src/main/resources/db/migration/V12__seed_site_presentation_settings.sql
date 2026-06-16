INSERT INTO portfolio.site_presentation_settings (
    id,
    singleton_key,
    site_name,
    default_language,
    title_suffix,
    default_meta_description,
    default_og_image_asset_id,
    default_theme,
    show_featured_projects,
    show_latest_writing,
    show_research_highlights,
    featured_project_limit,
    latest_writing_limit,
    updated_at,
    version
) VALUES (
    '00000000-0000-0000-0000-000000000701',
    true,
    'Persefonia',
    'TR',
    NULL,
    NULL,
    NULL,
    'SYSTEM',
    true,
    true,
    false,
    3,
    5,
    now(),
    0
);

INSERT INTO portfolio.site_supported_languages (settings_id, language)
VALUES
    ('00000000-0000-0000-0000-000000000701', 'TR'),
    ('00000000-0000-0000-0000-000000000701', 'EN');
