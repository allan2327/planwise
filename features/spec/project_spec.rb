describe "Project" do
  before(:each) {
    log_in
    create_dataset
  }
    context "projects listing" do
      before(:each) {   
        create_project("Foo")
      }

      it "should search project" do
        goto_page HomePage do |page|
          create_project("FooBar")  
        end
        goto_page HomePage do |page| 
          fill_in "search", :with => "Foo"
        end 
        expect_page HomePage do |page|
          expect(page).to have_content("Foo")
          expect(page).to_not have_content("FooBar")
          page.press_signout_button
        end
      end

      it "should access only allowed projects" do
        goto_page HomePage do |page|
          page.press_signout_button
          expect_page GuissoLogin do |page|
            page.form.user_name.set "user@instedd.org"
            page.form.password.set "user1234"
            page.form.login.click
          end
          expect_page HomePage do |page|
            expect(page).to_not have_content("Foo")
            expect(page).to have_content("You have no projects yet")
            page.press_signout_button
          end
        end  
      end
    end

    context "project view" do
      before(:each) {   
        create_project("Foo")
      }

      it "should delete a project" do
        goto_page HomePage do |page|
          expect(page).to have_content("Foo")
          open_project_view
        end
        expect_page ProjectPage do |page|
          page.header.delete.click
          accept_alert
        end
        expect_page HomePage do |page|
          expect(page).to_not have_content("Foo")
          page.press_signout_button
        end
      end

      it "should update transport means" do
        goto_page HomePage do |page|
          open_project_view
        end

        expect_page ProjectPage do |page|
          page.header.open_facilities
        end

        expect_page ProjectFacilitiesPage do |page|
          check_all_facility_types
          page.header.open_transport_means
        end

        expect_page ProjectTransportMeansPage do |page|   
          screen1 = screenshot_image
          sleep 3
          expand_options
          select_option(3)
          sleep 3
          screen2 = screenshot_image

          expect(screen1.duplicate?(screen2)).to be_falsey   
        end
      end

      it "should display place markers on the map" do
        goto_page HomePage do |page|
          open_project_view
        end

        expect_page ProjectPage do |page|
          page.header.open_facilities
        end

        expect_page ProjectFacilitiesPage do |page|     
          screen1 = screenshot_image
          check_all_facility_types
          page.zoom_in
          screen2 = screenshot_image
          
          expect(screen1.duplicate?(screen2)).to be_falsey   
        end
      end

      it "should change zoom level on the map" do
        goto_page HomePage do |page|
          open_project_view
        end

        expect_page ProjectPage do |page|
          page.header.open_facilities
        end

        expect_page ProjectFacilitiesPage do |page|     
          screen1 = screenshot_image
          check_all_facility_types
          4.times do
            page.zoom_in
          end
          screen2 = screenshot_image
  
          expect(screen1.duplicate?(screen2)).to be_falsey
        end
      end 


      it "should share a project" do
        goto_page HomePage do |page|
          expect(page).to have_content("Foo")
          open_project_view
        end

        expect_page ProjectPage do |page|
          page.header.share.click
          find(".send-email").set("user@instedd.org")
          $link = page.find('.share-link').value
          page.find(".primary").click
        end

        goto_page HomePage do |page|
          page.press_signout_button
        end

        expect_page GuissoLogin do |page|
          page.form.user_name.set "user@instedd.org"
          page.form.password.set "user1234"
          page.form.login.click
        end

        expect_page HomePage do |page|
          visit $link

          expect(page).to have_content("Foo")
          expect(page).to have_content("Leave project")
        end
      end          
    end

    context "without project" do
      it "should create project" do
        goto_page HomePage do |page|  
          page.press_primary_button 
          fill_in  "goal", :with => "FooBar"
          expand_options
          select_option(1)
          expand_locations_options
          select_location
          submit
          page.press_signout_button
        end 
      end
    end
end 