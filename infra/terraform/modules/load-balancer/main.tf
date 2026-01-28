# HTTPS Load Balancer Module
# Routes traffic to Cloud Storage (frontend) and Cloud Run (backend)

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "name" {
  description = "Load balancer name prefix"
  type        = string
}

variable "domain" {
  description = "Domain name for SSL certificate"
  type        = string
}

variable "frontend_bucket_name" {
  description = "Frontend Cloud Storage bucket name"
  type        = string
}

variable "backend_neg_id" {
  description = "Backend Network Endpoint Group ID (Cloud Run)"
  type        = string
}

# Reserve global external IP
resource "google_compute_global_address" "default" {
  project = var.project_id
  name    = "${var.name}-ip"
}

# Managed SSL certificate
resource "google_compute_managed_ssl_certificate" "default" {
  project = var.project_id
  name    = "${var.name}-cert"

  managed {
    domains = [var.domain]
  }
}

# Backend bucket for frontend static files
resource "google_compute_backend_bucket" "frontend" {
  project     = var.project_id
  name        = "${var.name}-frontend-bucket"
  bucket_name = var.frontend_bucket_name
  enable_cdn  = true

  cdn_policy {
    cache_mode        = "CACHE_ALL_STATIC"
    default_ttl       = 3600
    max_ttl           = 86400
    negative_caching  = true
    serve_while_stale = 86400
  }
}

# Backend service for Cloud Run
resource "google_compute_backend_service" "backend" {
  project = var.project_id
  name    = "${var.name}-backend-service"

  protocol    = "HTTPS"
  port_name   = "http"
  timeout_sec = 30

  backend {
    group = var.backend_neg_id
  }

  log_config {
    enable      = true
    sample_rate = 1.0
  }
}

# URL map for routing
resource "google_compute_url_map" "default" {
  project         = var.project_id
  name            = "${var.name}-url-map"
  default_service = google_compute_backend_bucket.frontend.id

  host_rule {
    hosts        = [var.domain]
    path_matcher = "main"
  }

  path_matcher {
    name            = "main"
    default_service = google_compute_backend_bucket.frontend.id

    path_rule {
      paths   = ["/api/*", "/health"]
      service = google_compute_backend_service.backend.id
    }
  }
}

# HTTPS proxy
resource "google_compute_target_https_proxy" "default" {
  project          = var.project_id
  name             = "${var.name}-https-proxy"
  url_map          = google_compute_url_map.default.id
  ssl_certificates = [google_compute_managed_ssl_certificate.default.id]
}

# Forwarding rule (HTTPS)
resource "google_compute_global_forwarding_rule" "https" {
  project    = var.project_id
  name       = "${var.name}-https-rule"
  target     = google_compute_target_https_proxy.default.id
  port_range = "443"
  ip_address = google_compute_global_address.default.address
}

# HTTP to HTTPS redirect
resource "google_compute_url_map" "https_redirect" {
  project = var.project_id
  name    = "${var.name}-https-redirect"

  default_url_redirect {
    https_redirect         = true
    strip_query            = false
    redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
  }
}

resource "google_compute_target_http_proxy" "https_redirect" {
  project = var.project_id
  name    = "${var.name}-http-proxy"
  url_map = google_compute_url_map.https_redirect.id
}

resource "google_compute_global_forwarding_rule" "http" {
  project    = var.project_id
  name       = "${var.name}-http-rule"
  target     = google_compute_target_http_proxy.https_redirect.id
  port_range = "80"
  ip_address = google_compute_global_address.default.address
}

output "ip_address" {
  description = "Load balancer IP address"
  value       = google_compute_global_address.default.address
}

output "https_url" {
  description = "HTTPS URL"
  value       = "https://${var.domain}"
}
