# frozen_string_literal: true

require 'rails_helper'

RSpec.feature 'managing api credentials' do
  context 'as an unassigned user' do
    let!(:user) { create :user }

    before(:each) do
      sign_in user, scope: :user
    end

    it 'cannot manage api credentials' do
      visit dashboard_path
      expect(page).not_to have_css('[data-test="new-client-token"]')
      expect(page).not_to have_css('[data-test="new-public-key"]')
    end
  end

  context 'as a user assign to an org that is not credentialiable' do
    let!(:user) { create :user, :assigned }

    before(:each) do
      org = user.organizations.first
      org.update(npi: nil)

      sign_in user, scope: :user
    end

    it 'cannot manage api credentials' do
      visit dashboard_path
      expect(page).not_to have_css('[data-test="new-client-token"]')
      expect(page).not_to have_css('[data-test="new-public-key"]')
    end
  end

  context 'as an assigned user' do
    let!(:user) { create :user, :assigned }

    before(:each) do
      org = user.organizations.first
      org.update(api_environments: [0], npi: SecureRandom.uuid)
      create(:registered_organization, organization: org, api_env: 0, api_id: '923a4f7b-eade-494a-8ca4-7a685edacfad')

      sign_in user, scope: :user
    end

    scenario 'creating and viewing a client token' do
      stub_token_creation_request
      stub_token_get_request
      stub_key_get_request

      visit dashboard_path
      find('[data-test="new-client-token"]').click
      select 'sandbox', from: 'api_environment'
      fill_in 'label', with: 'Sandbox Token 1'
      find('[data-test="form-submit"]').click

      expect(page).to have_content('Sandbox Token 1')
      expect(page).to have_content('1234567890')
      expect(page).to have_content('11/07/2019 at 5:15PM UTC')

      find('[data-test="dashboard-link"]').click

      expect(page).to have_content('Sandbox Token 1')
      expect(page).to have_content('11/07/2019 at 5:15PM UTC')
      expect(page).not_to have_content('1234567890')
    end

    scenario 'creating and viewing a public key' do
      stub_key_creation_request
      stub_key_get_request
      stub_token_get_request
      visit dashboard_path
      find('[data-test="new-public-key"]').click
      select 'sandbox', from: 'api_environment'
      fill_in 'label', with: 'Sandbox Key 1'
      fill_in 'public_key', with: stubbed_key
      find('[data-test="form-submit"]').click

      # expect page location to be dashboard path
      expect(page).to have_content('3fa85f64-5717-4562-b3fc-2c963f66afa6')
    end
  end

  def stubbed_key
    file_fixture('stubbed_key.pem').read
  end

  def stub_key_creation_request
    allow(ENV).to receive(:fetch).with('API_METADATA_URL_SANDBOX').and_return('http://dpc.example.com')
    allow(ENV).to receive(:fetch).with('GOLDEN_MACAROON_SANDBOX').and_return('MDAxY2xvY2F0aW9uIGh0dHA6Ly9teWJhbmsvCjAwMjZpZGVudGlmaWVyIHdlIHVzZWQgb3VyIHNlY3JldCBrZXkKMDAxNmNpZCB0ZXN0ID0gY2F2ZWF0CjAwMmZzaWduYXR1cmUgGXusegRK8zMyhluSZuJtSTvdZopmDkTYjOGpmMI9vWcK')
    stub_request(:post, 'http://dpc.example.com/Key?label=Sandbox+Key+1').with(
      body: stubbed_key
    ).to_return(
      status: 200,
      body: { label: 'Sandbox Key 1', createdAt: '2019-11-07T19:38:44.205Z', id: '3fa85f64-5717-4562-b3fc-2c963f66afa6' }.to_json
    )
  end

  def stub_key_get_request
    stub_request(:get, 'http://dpc.example.com/Key')
      .to_return(
        status: 200,
        body:
          '{
            "entities": [{
                "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "publicKey": "---PUBLIC KEY---......---END PUBLIC KEY---",
                "createdAt": "2019-11-14T19:47:44.574Z",
                "label": "example public key"
              }],
            "count": 1,
            "created_at": "2019-11-14T19:47:44.574Z"
          }'
      )
  end

  def stub_token_creation_request
    allow(ENV).to receive(:fetch).with('API_METADATA_URL_SANDBOX').and_return('http://dpc.example.com')
    allow(ENV).to receive(:fetch).with('GOLDEN_MACAROON_SANDBOX').and_return('MDAyM2xvY2F0aW9uIGh0dHA6Ly9sb2NhbGhvc3Q6MzAwMgowMDM0aWRlbnRpZmllciBiODY2NmVjMi1lOWY1LTRjODctYjI0My1jMDlhYjgyY2QwZTMKMDAyZnNpZ25hdHVyZSA1hzDOqfW_1hasj-tOps9XEBwMTQIW9ACQcZPuhAGxwwo')
    stub_request(:post, 'http://dpc.example.com/Token').with(
      body: { label: 'Sandbox Token 1' }.to_json
    ).to_return(
      status: 200,
      body: { token: '1234567890', label: 'Sandbox Token 1', createdAt: '2019-11-07T17:15:22.781Z' }.to_json
    )
  end

  def stub_token_get_request
    stub_request(:get, "http://dpc.example.com/Token")
      .to_return(
        status: 200,
        body: {
          entities: [
            {
              id: '456a4f7b-ttwe-494a-8ca4-7a685edalrep',
              tokenType: 'MACAROON',
              label: 'Sandbox Token 1',
              createdAt: '2019-11-07T17:15:22.781Z',
              expiresAt: '2019-11-07T17:15:22.781Z'
            }
          ]
        }.to_json
      )
  end
end
