-- =====================================================================
-- Seed data: default escalation ladder (7-day SLA per level),
-- integration placeholders and sample directory entries.
-- Users are seeded by DataSeeder.java so passwords get BCrypt-hashed at runtime.
-- =====================================================================

INSERT INTO escalation_rule (level, level_order, notify_role, start_day, end_day,
    notify_portal, notify_sms, notify_email, notify_call, notify_caller_sms, reminder_hours,
    sms_template, email_template) VALUES
('FACILITY', 1, 'FACILITY_OFFICER', 1, 7, TRUE, TRUE, TRUE, TRUE, TRUE, 24,
 'Grievance {grievanceNo} assigned to you. Please resolve within {daysLeft} days. -104 Helpline',
 'Dear {officerName},<br/>Grievance <b>{grievanceNo}</b> ({category}) has been assigned to you. SLA: {daysLeft} days.'),
('DISTRICT', 2, 'DISTRICT_OFFICER', 8, 14, TRUE, TRUE, TRUE, TRUE, TRUE, 24,
 'ESCALATED: Grievance {grievanceNo} not resolved at facility level. Action required in {daysLeft} days. -104',
 'Dear {officerName},<br/>Grievance <b>{grievanceNo}</b> has been escalated to District level.'),
('STATE', 3, 'STATE_OFFICER', 15, 21, TRUE, TRUE, TRUE, TRUE, TRUE, 12,
 'ESCALATED: Grievance {grievanceNo} escalated to State level. -104',
 'Dear {officerName},<br/>Grievance <b>{grievanceNo}</b> has been escalated to State level.'),
('MINISTER', 4, 'MINISTER_OFFICE', 22, 28, TRUE, TRUE, TRUE, FALSE, TRUE, 12,
 'ESCALATED: Grievance {grievanceNo} escalated to Hon. Minister office. -104',
 'Grievance <b>{grievanceNo}</b> has been escalated to the Hon. State Health Minister office.');

INSERT INTO integration_config (code, display_name, provider, settings_json) VALUES
('TELECOM',     'Telecom / PRI (Toll-free 104)', 'BSNL',
 '{"tollFreeNumber":"104","priLines":"2","channelsPerPri":"30","sipTrunkHost":"","sipPort":"5060","inboundDid":"","outboundCli":"","recordingEnabled":"true","ivrWelcomeMsg":"Welcome to 104 Arogya Sahayata Kendra"}'),
('SMS',         'SMS Gateway', 'NIC / CDAC SMS',
 '{"senderId":"HLT104","dltEntityId":"","dltTemplateId":"","route":"TRANSACTIONAL","unicode":"true"}'),
('EMAIL',       'Email Gateway (SMTP)', 'SMTP',
 '{"smtpHost":"","smtpPort":"587","tls":"true","fromAddress":"noreply-104@example.gov.in","fromName":"104 Helpline"}'),
('GIS',         'Geographical Mapping (GIS)', 'Bhuvan / OSM',
 '{"tileUrl":"https://tile.openstreetmap.org/{z}/{x}/{y}.png","geocodeUrl":"","defaultLat":"21.2514","defaultLng":"81.6296","searchRadiusKm":"25"}'),
('MOHFW_RBSK',  'MoHFW - RBSK', 'MoHFW',
 '{"stateCode":"22","reportFrequency":"DAILY","pushEndpoint":"/api/rbsk/referrals"}'),
('MOHFW_PMJAY', 'MoHFW - AB-PMJAY', 'NHA',
 '{"stateCode":"22","beneficiaryLookupEndpoint":"/api/pmjay/beneficiary","grievanceEndpoint":"/api/pmjay/grievance"}'),
('AMBULANCE',   'State Ambulance / Emergency Transport (108/102)', 'State EMS',
 '{"emergencyNumber":"108","janiSurakshaNumber":"102","dispatchEndpoint":"/api/dispatch","autoShareLocation":"true"}');

INSERT INTO facility (name, type, district, address, phone, latitude, longitude, services) VALUES
('Dr. B.R. Ambedkar Memorial Hospital', 'HOSPITAL',   'Raipur', 'Jail Road, Raipur', '0771-2890000', 21.2570, 81.6380, 'Multi-speciality, Emergency'),
('District Hospital Durg',              'HOSPITAL',   'Durg',   'Durg',              '0788-2323232', 21.1900, 81.2849, 'General, Maternity'),
('Red Cross Blood Bank Raipur',         'BLOOD_BANK', 'Raipur', 'Shastri Chowk',     '0771-2530000', 21.2440, 81.6340, 'All groups, Platelets'),
('District Hospital Blood Bank Balod',  'BLOOD_BANK', 'Balod',  'Balod',             '07749-222222', 20.7300, 81.2050, 'All groups'),
('CHC Dharsiwa',                        'CHC',        'Raipur', 'Dharsiwa',          '0771-0000000', 21.3380, 81.5600, 'OPD, Delivery');

INSERT INTO public_authority (name, level, district, officer_name, officer_designation, officer_phone, officer_email) VALUES
('District Hospital Balod', 'FACILITY', 'Balod',  'Dr. A. Sharma', 'Civil Surgeon',   '9000000001', 'cs.balod@example.gov.in'),
('District Hospital Durg',  'FACILITY', 'Durg',   'Dr. R. Verma',  'Civil Surgeon',   '9000000002', 'cs.durg@example.gov.in'),
('CMHO Office Raipur',      'DISTRICT', 'Raipur', 'Dr. S. Gupta',  'CMHO',            '9000000003', 'cmho.raipur@example.gov.in'),
('Directorate of Health Services', 'STATE', NULL, 'Dr. P. Singh', 'Director Health', '9000000004', 'dhs@example.gov.in'),
('Office of Hon. Health Minister', 'MINISTER', NULL, 'OSD', 'Officer on Special Duty', '9000000005', 'hm.office@example.gov.in');
