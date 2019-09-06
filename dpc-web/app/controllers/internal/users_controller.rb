# frozen_string_literal: true

module Internal
  class UsersController < ApplicationController
    before_action :authenticate_internal_user!

    def index
      @users = User.all
    end
  end
end
