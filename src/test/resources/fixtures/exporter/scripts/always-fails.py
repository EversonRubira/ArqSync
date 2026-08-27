#!/usr/bin/env python3
"""Test fixture: a script that always fails, for DefaultReportExporterTest."""
import sys

print("simulated failure for testing", file=sys.stderr)
sys.exit(1)
