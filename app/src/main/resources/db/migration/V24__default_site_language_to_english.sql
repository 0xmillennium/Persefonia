INSERT INTO portfolio.site_supported_languages (settings_id, language)
SELECT id, 'EN'
FROM portfolio.site_presentation_settings
ON CONFLICT (settings_id, language) DO NOTHING;

UPDATE portfolio.site_presentation_settings
SET default_language = 'EN', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE default_language <> 'EN';
