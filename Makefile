.PHONY: test docker-test build stage release clean lint run logs patch minor major
MAKELEVELGOAL := $(word 2,$(MAKECMDGOALS))
ifneq ($(filter patch minor major,$(MAKELEVELGOAL)),)
  ifeq ($(origin LEVEL),undefined)
    LEVEL := $(MAKELEVELGOAL)
  endif
endif

# Default release level is patch unless overridden by LEVEL var or positional goal
LEVEL ?= patch

# Define empty phony targets for patch, minor, major to avoid missing rule errors
patch:
minor:
major:

# Testing
test:
	lein test

lint:
	lein clj-kondo
	lein kibit || true

# Building
build:
	lein ring uberjar

docker-build:
	docker build --platform linux/amd64 -t bibcal-www .

# Running
run:
	lein ring server

docker-run:
	docker-compose up --build

# Release & Deployment
# Usage: make release [LEVEL=major|minor|patch]
release:
	@echo "Creating release using Leiningen..."
	@echo "Release level: $(LEVEL)"
	@lein release :$(LEVEL)
	@echo "Release complete! GitHub Actions will deploy to www.bibcal.org"

stage:
	@echo "Deploying to next.bibcal.org (staging)..."
	fly deploy --config fly.staging.toml -a bibcal-staging

# Cleanup
clean:
	lein clean
	rm -rf .cpcache
	rm -rf target

# Help
help:
	@echo "Available commands:"
	@echo "  === Testing ==="
	@echo "  make test          - Run tests locally"
	@echo "  make lint          - Run linters"
	@echo ""
	@echo "  === Building ==="
	@echo "  make build         - Build uberjar"
	@echo "  make docker-build  - Build Docker image"
	@echo ""
	@echo "  === Running ==="
	@echo "  make run           - Run app locally with live reload"
	@echo "  make docker-run    - Run app in Docker"
	@echo ""
	@echo "  === Release & Deployment ==="
	@echo "  make release       - Bump patch version, tag, and push to GitHub (triggers production deploy)"
	@echo "  make release LEVEL=minor - Bump minor version"
	@echo "  make release LEVEL=major - Bump major version"
	@echo "  make stage         - Deploy current code to next.bibcal.org (staging)"
	@echo ""
	@echo "  === Maintenance ==="
	@echo "  make clean         - Remove build artifacts"
