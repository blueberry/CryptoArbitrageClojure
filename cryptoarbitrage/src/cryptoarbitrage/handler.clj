(ns cryptoarbitrage.handler
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [cryptoarbitrage.mongodb :as mongo]
            [cryptoarbitrage.handler-helper :as helper]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [cryptoarbitrage.cryptoapis :as apis]
            [clojure.data.json :as json]
            [postal.core :as email]
            ))

(defn login [req]
  (let
    [json_body (doto (helper/read-body req))
     email (doto (:email json_body))
     password (doto (:password json_body))
     ]
    (if (and (not (str/blank? email)) (not (str/blank? password)))
      (let [doc_user (doto(mongo/find "users" {:email email}))
            doc_password (doto (:password doc_user))]
        (if doc_user
          (if (= doc_password password)
            (helper/form-success {:message "User successfully logged in"
                                  :user doc_user
                                  })
            (helper/form-fail  {:message "Wrong password"}))
          (helper/form-fail {:message "User not found"})))
      (helper/form-fail {:message "Email and password cannot be empty"})
      )
    )
  )

(defn register [req]
  (let
    [json_body (doto (helper/read-body req))
     email (doto(:email json_body))
     password (doto (:password json_body))
     name (doto (:name json_body))
     username (doto (:username json_body))
     country (doto (:country json_body))
     admin (doto (if (= "admin_password" (:admin_password json_body)) true false ))
     _id (doto (helper/uuid))
     ]
    (println "got register " json_body)
    (println admin)
    (println :admin_password json_body)
    (if (and (not(str/blank? email)) (not(str/blank? password)) (not (str/blank? name)) (not (str/blank? username))  (not (nil? country)))
      (let [country (mongo/find "countries" {:_id (:_id country)})]
        (if country
         (if (mongo/find "users" {:email email})
           (helper/form-fail {:message "That email is already used"})
           (do (mongo/insert "users" (-> json_body
                                         (assoc :_id _id)
                                         (assoc :admin admin)
                                         (assoc :country country)))
               (helper/form-success {:message "User successfully registered"
                                     :user    (-> json_body
                                                  (assoc :_id _id)
                                                  (assoc :admin admin)
                                                  (assoc :country country))})))
         (helper/form-fail {:message "That country doesn't exist"})))
      (helper/form-fail {:message "Email, password, username, country and name cannot be empty"}))
    )
)
(defn get_me [id]
  (if (and (not (str/blank? id)))
    (let [doc_user (mongo/find "users" {:_id id})]
      (if doc_user
        (helper/form-success (apply dissoc doc_user [:_id]))
        (helper/form-fail {:message "User doesn't exist"})))
    (helper/form-fail {:message "Email cannot be empty"})
    )
  )
(defn update_me [id req]
     (let
       [json_body (doto (helper/read-body req))
        email (doto(:email json_body))
        password (doto (:password json_body))
        name (doto (:name json_body))
        username (doto (:username json_body))
        country (doto (:country json_body))
        ]
       (if (and (not(str/blank? email)) (not(str/blank? password)) (not (str/blank? name)) (not (str/blank? username)) (not (nil? country)))
         (if (mongo/find "users" {:_id id})
           (let [new_country (mongo/find "countries" {:_id (:_id country)})]
             (if new_country
              (do (mongo/update "users" {:_id id} (-> json_body
                                                      (assoc :_id id)
                                                      (assoc :country new_country)))
                  (helper/form-success {:message "User successfully updated"
                                        :user (-> json_body
                                                  (assoc :_id id)
                                                  (assoc :country new_country))}))
              (helper/form-fail {:message "That country doesn't exist"})))
           (helper/form-fail {:message "That id is not found"}))
         (helper/form-fail {:message "Email, password, username, country and name cannot be empty"}))
       )
     )

(defn change-password [id req]
  (let [json_body (doto (helper/read-body req))
        password (doto (:password json_body))]
    (if (not (str/blank? password))
      (let [user (mongo/find "users" {:_id id})]
        (mongo/update "users" {:_id id} (assoc user :password password))
        (helper/form-success {:message "Successful password update"})
        )
      (helper/form-fail "Password cannot be empty")
      )
    ))


(defn upload-profile-picture
  [id req]
  (if (not (nil? (mongo/find "users" {:_id id})))
    (if (and (not (nil? (:body req))))
      (let [picture (helper/slurp-bytes (:body req))]
       (if (and (not (nil? picture)) (not (<= (count picture) 17)))
         (do
           (when (not (.exists (io/file "resources/public/profilepictures")))
             (.mkdir (io/file "resources/public/profilepictures")))
           (with-open [w (io/output-stream (str "resources/public/profilepictures/" id ".jpg"))]
             (.write w picture))
           (helper/form-success {:message "Successfully uploaded profile picture"})
           )
         (helper/form-fail {:message "Picture cannot be empty"})
         ))
      (helper/form-fail {:message "Picture cannot be null"}))
    (helper/form-fail {:message "User not found"})))

(defn get_countries []
  (helper/form-success (mongo/find-all "countries"))
  )

(defn get_pairs []
  (helper/form-success (mongo/find-all "duplicate_pairs"))
  )
;(map (apis/get-price (str/split (:label %) #"/") (str/split (:label %) #"/") "CEX"))

(defn get-price-on-exchanges []
  ;(helper/form-success (apis/get-price-on-exchanges))
  (let [last_prices (mongo/find "prices" {:_id 1})]
    (if last_prices
      (helper/form-success last_prices)
      (let [new_prices (apis/get-all-supported-pairs-prices-on-exchanges)]
        (mongo/insert "prices" (-> new_prices
                                   (assoc :_id 1)
                                   (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date)))))
        (helper/form-success (-> new_prices
                                 (assoc :_id 1)
                                 (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date)))))
        )
      )
     )
  )

(defn refresh-price-on-exchanges []
  ;(helper/form-success (apis/get-price-on-exchanges))
  (let [last_prices (mongo/find "prices" {:_id 1})
        new_prices  (apis/get-all-supported-pairs-prices-on-exchanges) ;last_prices
        ]
    (if last_prices
      (do (mongo/update "prices" {:_id 1} (-> new_prices
                                              (assoc :_id 1)
                                              (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date)))))
          (helper/form-success (-> new_prices
                                   (assoc :_id 1)
                                   (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date)))))
          )
      (do (mongo/insert "prices" (-> new_prices
                                     (assoc :_id 1)
                                     (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date)))))
          (helper/form-success (-> new_prices
                                   (assoc :_id 1)
                                   (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date))))))
      )
    )
  )

(defn populate_countries [password]
  (if (= password "admin")
    (do
      (mongo/drop "countries")
      (let [all_countries (json/read-json (:body (client/get "https://restcountries.eu/rest/v2/all")))]
        (loop [x 0]
          (when (< x (count all_countries))
            (mongo/insert "countries" (assoc (select-keys (get all_countries x) [:name :alpha2Code :alpha3Code]) :_id (:alpha2Code (get all_countries x))
                                                                                                                 :id (:alpha2Code (get all_countries x))
                                                                                                                 :label (:name (get all_countries x))
                                                                                                                 :group (get (:name (get all_countries x)) 0)))
            (recur (+ x 1)))
          )
        )
      (helper/form-success {:message "Successfully populated countries"})
      )
    (helper/form-fail {:message "Wrong password"})
    ))

(defn populate_countries_req [req]
  (let [json_body (doto (helper/read-body req))
        password (doto (:password json_body))]
   (populate_countries [password])
    )
  )

(defn save_blog_post [id req]
  (let [json_body (doto (helper/read-body req))
        title (doto (:title json_body))
        description (doto (:description json_body))
        text (doto (:text json_body))
        text_fixed (doto (str/replace text "<!--block-->" ""))
        ]
    (if (or (str/blank? title) (str/blank? description) (str/blank? text))
      (helper/form-fail {:message "Title, description and text can't be empty"})
      (let [user (mongo/find "users" {:_id id})
            all_blog_posts (mongo/find-all "blog_posts")
            last_post (last all_blog_posts)
            ]
        (if (nil? user)
          (helper/form-fail {:message "User is not found"})
          (do (mongo/insert "blog_posts" (-> json_body
                                          (assoc :user_id id)
                                          (assoc :user user)
                                             (assoc :thumbs 0)
                                             (assoc :text text_fixed)
                                          (assoc :_id (if (not (nil? last_post)) (+ 1 (:_id last_post)) 0))
                                             (assoc :thumbs_by (list))
                                          (assoc :date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy HH:mm:ss") (new java.util.Date)))
                                          )
                         )
              (helper/form-success {:message "Blog post successfully posted"})
              )
          )
        )
      )
    )
  )

(defn blog_post_thumbs_up [id req]
  (let [json_body (doto (helper/read-body req))
        rating_user_id (doto (:id json_body))
        ]
    (if (or (str/blank? rating_user_id))
      (helper/form-fail {:message "User id that is rating can't be empty"})
      (let [post (mongo/find "blog_posts" {:_id (Integer/parseInt id)})]
        (if (nil? post)
          (helper/form-fail {:message "That post is not found"})
          (if (nil? (mongo/find "users" {:_id rating_user_id}))
            (helper/form-fail {:message "That user doesn't exist"})
            (if (.contains (:thumbs_by post) rating_user_id)
             ;(helper/form-fail {:message "You have already rated this post"})
             (do
               (mongo/update "blog_posts" {:_id (Integer/parseInt id)}
                             (-> post
                                 (assoc :thumbs (+ 1 (:thumbs post)))
                                 (assoc :thumbs_by (conj (:thumbs_by post) rating_user_id) )))
               (helper/form-success {:message "Thank you for rating"})
               )

             (do
               (mongo/update "blog_posts" {:_id (Integer/parseInt id)}
                               (-> post
                                   (assoc :thumbs (+ 1 (:thumbs post)))
                                   (assoc :thumbs_by (conj (:thumbs_by post) rating_user_id) )))
                 (helper/form-success {:message "Thank you for rating"})
                 )
             )
            )

          )
        )
      )
    )
  )
(defn blog_post_thumbs_down [id req]
  (let [json_body (doto (helper/read-body req))
        rating_user_id (doto (:id json_body))
        ]
    (if (or (str/blank? rating_user_id))
      (helper/form-fail {:message "User id that is rating can't be empty"})
      (let [post (mongo/find "blog_posts" {:_id (Integer/parseInt id)})]
        (if (nil? post)
          (helper/form-fail {:message "That post is not found"})
          (if (nil? (mongo/find "users" {:_id rating_user_id}))
            (helper/form-fail {:message "That user doesn't exist"})
            (if (.contains (:thumbs_by post) rating_user_id)
              ;(helper/form-fail {:message "You have already rated this post"})
              (do
                (mongo/update "blog_posts" {:_id (Integer/parseInt id)}
                              (-> post
                                  (assoc :thumbs (- 1 (:thumbs post)))
                                  (assoc :thumbs_by (conj (:thumbs_by post) rating_user_id) )))
                (helper/form-success {:message "Thank you for rating"})
                )
              (do
                (mongo/update "blog_posts" {:_id (Integer/parseInt id)}
                              (-> post
                                  (assoc :thumbs (- 1 (:thumbs post)))
                                  (assoc :thumbs_by (conj (:thumbs_by post) rating_user_id) )))
                (helper/form-success {:message "Thank you for rating"})
                )
              )
            )

          )
        )
      )
    )
  )




(defn get_blog_posts []
  (helper/form-success (reverse (mongo/find-all "blog_posts")))

  )

(defn get_blog_posts_sorted []
  (helper/form-success (reverse (sort-by :thumbs (mongo/find-all "blog_posts"))))
  )

(defn get_my_blog_posts [id]
  (helper/form-success(mongo/find-all-with-query "blog_posts" {:user_id id}))
  )
(defn populate_exchanges [password]
  (if (= password "admin")
    (do
      (mongo/drop "exchanges")
      (mongo/insert "exchanges" {:_id "CEX" :name "CEX.IO" :website "https://cex.io/" })
      (mongo/insert "exchanges" {:_id "BST" :name "BitStamp" :website "https://www.bitstamp.net/" })
      (mongo/insert "exchanges" {:_id "BFX" :name "BitFinex" :website "https://www.bitfinex.com/" })
      (helper/form-success {:message "Successfully populated exchanges"})
      )
    (helper/form-fail {:message "Wrong password"})
    ))
(defn populate_exchanges_req [req]
  (let [json_body (doto (helper/read-body req))
        password (doto (:password json_body))]
    (populate_exchanges [password])
    )
  )

(defn populate_pairs [password]
  (if (= password "admin")
    (do (mongo/drop "pairs")
        (mongo/drop "unique_pairs")
        (mongo/drop "duplicate_pairs")
        (apis/cex_io-populate-all-pairs false)
        (apis/bit_stamp-populate-all-pairs false)
        (apis/bit_finex-populate-all-pairs false)
        (apis/populate-duplicated false)
        (helper/form-success {:message "Successfully populated pairs"})
        )
    (helper/form-fail {:message "Wrong password"})
    )
  )
(defn populate_pairs_req [req]
  (let [json_body (doto (helper/read-body req))
        password (doto (:password json_body))]
    (populate_pairs password)
    )
  )

(defn email-me [req]
  (let [json_body (doto (helper/read-body req))
        name (doto (:name json_body))
        email (doto (:name json_body))
        subject (doto (:name json_body))
        message (doto (:name json_body))
        ]
    (if (and (not(str/blank? email)) (not(str/blank? subject)) (not (str/blank? name)) (not (str/blank? message)))
      (do
           (email/send-message {:host "smtp.1and1.com"
                                :port 587
                                      :auth true
                                :user "test@miracledojo.com"
                                :pass "Thisistestemail.123"}
                               {:from    "test@miracledojo.com"
                                :to      "skoncar@live.com"      ;;SAMO OVO SME DA SE PROMENI AKO SE SALJE NA DRUGI MEJL
                                :subject subject
                                :body    [:alternative
                                          {:type    "text/plain"
                                           :content "This is a test."}
                                          {:type    "text/html"
                                           :content (str "From: " email "<br>" "Message: <br>" message)}
                                          ]})
        (helper/form-success {:message "Email successfully sent"})
        )
      (helper/form-fail {:message "Email, subject, name and message cannot be empty"})
      )
    )
  )

(defn descending-price [a b]
     (helper/form-success {:message "Successfully collected descending prices" :result (apis/descending_price a b) :label "HIGHEST TO LOWEST"})
     )

(defn ascending-price [a b]
  (helper/form-success {:message "Successfully collected ascending prices" :result (apis/ascending_price a b) :label "LOWEST TO HIGHEST"})

  )

(defn inner-matrix [a b]
  (let [result (apis/get-inner-matrix a b)]
    (if (:success result)
      (helper/form-success result)
      (helper/form-fail result)
      )
    )
  )

(defn not_found []
  (helper/form-response 404 (helper/form-json_body {:message "requested resource is not found"})))

