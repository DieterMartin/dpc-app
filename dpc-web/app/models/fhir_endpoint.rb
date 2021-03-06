# frozen_string_literal: true

class FhirEndpoint < ApplicationRecord
  belongs_to :organization

  enum status: {
    'test' => 0,
    'active' => 1,
    'suspended' => 2,
    'error' => 3,
    'off' => 4,
    'entered-in-error' => 5
  }

  validates :name, :uri, :status, presence: true
  validate :uri_is_valid_format

  def uri_is_valid_format
    parsed_uri = URI.parse(self[:uri])
    errors.add :uri, 'must be valid URI' if parsed_uri.host.nil?
  rescue URI::InvalidURIError
    errors.add :uri, 'must be valid URI'
  end
end
