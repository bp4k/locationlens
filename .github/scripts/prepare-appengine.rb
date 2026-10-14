require "fileutils"
require "uri"
require "yaml"

def required_setting(name)
  value = ENV.fetch(name, "")
  abort "Missing required setting: #{name}" if value.strip.empty?
  value
end

project = required_setting("GCP_PROJECT_ID").strip
instance = required_setting("CLOUD_SQL_INSTANCE").strip
database = required_setting("CI_DB_NAME").strip
runtime_account = required_setting("GCP_RUNTIME_SERVICE_ACCOUNT").strip
issuer = required_setting("IDP_URI").strip

abort "CLOUD_SQL_INSTANCE must be project:region:instance in GCP_PROJECT_ID" unless
  instance.match?(/\A#{Regexp.escape(project)}:[a-z0-9-]+:[a-z0-9-]+\z/)
abort "CI_DB_NAME must contain only letters, numbers, or underscores" unless
  database.match?(/\A[a-zA-Z0-9_]+\z/)
abort "GCP_RUNTIME_SERVICE_ACCOUNT must be a service-account email" unless
  runtime_account.match?(/\A[a-zA-Z0-9-]+@[a-zA-Z0-9-]+\.iam\.gserviceaccount\.com\z/)
abort "IDP_URI must be an HTTPS issuer accessible from App Engine" unless
  issuer.match?(/\Ahttps:\/\/[^\s]+\z/)

config = YAML.safe_load(File.read("app.yaml"))
config["service_account"] = runtime_account
connection_options = URI.encode_www_form(
  "cloudSqlInstance" => instance,
  "socketFactory" => "com.google.cloud.sql.mysql.SocketFactory",
  "cloudSqlRefreshStrategy" => "lazy",
  "ipTypes" => "PUBLIC",
  "sslMode" => "DISABLED",
  "allowPublicKeyRetrieval" => "true"
)
config["env_variables"] = config.fetch("env_variables", {}).merge(
  "DB_URL" => "jdbc:mysql:///#{database}?#{connection_options}",
  "DB_USER" => required_setting("CI_DB_USERNAME"),
  "DB_PASS" => required_setting("CI_DB_PASSWORD"),
  "IDP_URI" => issuer,
  "IDP_CLIENT_ID" => required_setting("IDP_CLIENT_ID"),
  "IDP_CLIENT_SECRET" => required_setting("IDP_CLIENT_SECRET"),
  "VALID_REDIRECTS" => required_setting("VALID_REDIRECTS"),
  "FLYWAY_ENABLED" => "true",
  "DDL_AUTO" => "validate"
)

FileUtils.mkdir_p("target/appengine")
File.write("target/appengine/app.yaml", YAML.dump(config), perm: 0600)
FileUtils.cp("target/locationlens-0.0.1-SNAPSHOT.jar", "target/appengine/app.jar")
puts "App Engine deployment prepared; runtime credentials were not printed."